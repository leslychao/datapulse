import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import type { EChartsOption } from 'echarts';
import { Package, AlertTriangle, DollarSign, AlertCircle } from 'lucide-angular';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { InventoryByProduct } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { StockRiskBadgeComponent } from '@shared/components/stock-risk-badge.component';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { DateRangePickerComponent } from '@shared/components/form/date-range-picker.component';
import { formatMoney } from '@shared/utils/format.utils';

const COLLAPSED_ROW_LIMIT = 5;

function resolveCssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || name;
}

function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

function pctDelta(first: number, last: number): number | null {
  if (first === 0) return null;
  return ((last - first) / first) * 100;
}

@Component({
  selector: 'dp-inventory-overview-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    TranslatePipe,
    ChartComponent,
    StockRiskBadgeComponent,
    KpiCardComponent,
    DateRangePickerComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <div class="flex h-full flex-col overflow-y-auto">
      <!-- KPI cards -->
      <div class="flex flex-wrap gap-3 px-4 pt-3">
        <dp-kpi-card
          [label]="'analytics.inventory.kpi.total_skus' | translate"
          [value]="kpiTotalSkus()"
          [icon]="PackageIcon"
          accent="neutral"
          [loading]="overviewQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'analytics.inventory.kpi.critical' | translate"
          [value]="kpiCritical()"
          [icon]="AlertTriangleIcon"
          accent="error"
          [loading]="overviewQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'analytics.inventory.kpi.warning' | translate"
          [value]="kpiWarning()"
          [icon]="AlertCircleIcon"
          accent="warning"
          [loading]="overviewQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'analytics.inventory.kpi.frozen_capital' | translate"
          [value]="kpiFrozenCapital()"
          [icon]="DollarSignIcon"
          accent="neutral"
          [loading]="overviewQuery.isPending()"
        />
      </div>

      <!-- Risk distribution chart -->
      <div class="mx-4 mt-3 rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
        <p class="mb-2 text-sm font-medium text-[var(--text-primary)]">
          {{ 'analytics.inventory.risk_distribution' | translate }}
        </p>
        <dp-chart
          [options]="riskChartOptions()"
          [loading]="overviewQuery.isPending()"
          height="120px"
        />
      </div>

      <!-- Stock dynamics (moved from History tab) -->
      <div class="mx-4 mt-3 rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
        <div class="mb-3 flex items-center justify-between">
          <p class="text-sm font-medium text-[var(--text-primary)]">
            {{ 'analytics.inventory.dynamics.title' | translate }}
          </p>
          <dp-date-range-picker
            [from]="dateFrom()"
            [to]="dateTo()"
            (fromChange)="dateFrom.set($event)"
            (toChange)="dateTo.set($event)"
          />
        </div>
        <dp-chart
          [options]="dynamicsChartOptions()"
          [loading]="dynamicsQuery.isPending()"
          height="280px"
        />

        <!-- Dynamics KPI strip -->
        @if (!dynamicsQuery.isPending() && dynamicsKpi().lastAvailable !== null) {
          <div class="mt-3 grid grid-cols-3 gap-3 border-t border-[var(--border-subtle)] pt-3">
            <div>
              <p class="text-xs text-[var(--text-secondary)]">
                {{ 'analytics.inventory.dynamics.total_available' | translate }}
              </p>
              <p class="mt-0.5 font-mono text-lg font-semibold text-[var(--text-primary)]">
                {{ dynamicsKpi().lastAvailable?.toLocaleString('ru-RU') ?? '—' }}
              </p>
            </div>
            <div>
              <p class="text-xs text-[var(--text-secondary)]">
                {{ 'analytics.inventory.dynamics.total_reserved' | translate }}
              </p>
              <p class="mt-0.5 font-mono text-lg font-semibold text-[var(--text-primary)]">
                {{ dynamicsKpi().lastReserved?.toLocaleString('ru-RU') ?? '—' }}
              </p>
            </div>
            <div>
              <p class="text-xs text-[var(--text-secondary)]">
                {{ 'analytics.inventory.dynamics.change' | translate }}
              </p>
              @if (dynamicsKpi().deltaPct !== null) {
                <p
                  class="mt-0.5 font-mono text-lg font-semibold"
                  [class]="dynamicsKpi().deltaPct! >= 0
                    ? 'text-[var(--finance-positive)]'
                    : 'text-[var(--finance-negative)]'"
                >
                  {{ dynamicsKpi().deltaPct! >= 0 ? '+' : '' }}{{ dynamicsKpi().deltaPct! | number:'1.1-1' }}%
                </p>
              } @else {
                <p class="mt-0.5 font-mono text-lg font-semibold text-[var(--text-primary)]">—</p>
              }
            </div>
          </div>
        }
      </div>

      <!-- Top critical products -->
      <div class="mx-4 mt-3 mb-4 rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
        <p class="mb-3 text-sm font-medium text-[var(--text-primary)]">
          {{ 'analytics.inventory.top_critical' | translate }}
        </p>
        @if (overviewQuery.isPending()) {
          <div class="dp-shimmer h-48 w-full rounded"></div>
        } @else if (topCritical().length === 0) {
          <p class="py-6 text-center text-sm text-[var(--text-tertiary)]">
            {{ 'analytics.inventory.no_critical' | translate }}
          </p>
        } @else {
          <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-subtle)]">
            <table class="w-full text-[length:var(--text-sm)]">
              <thead>
                <tr class="bg-[var(--bg-secondary)] text-[var(--text-secondary)]" style="height: 38px">
                  <th class="px-2.5 text-left font-medium">SKU</th>
                  <th class="px-2.5 text-left font-medium">{{ 'analytics.inventory.col.product' | translate }}</th>
                  <th class="px-2.5 text-left font-medium">{{ 'analytics.inventory.col.platform' | translate }}</th>
                  <th class="px-2.5 text-right font-medium">{{ 'analytics.inventory.col.available' | translate }}</th>
                  <th class="px-2.5 text-right font-medium">{{ 'analytics.inventory.col.days_of_cover' | translate }}</th>
                  <th class="px-2.5 text-left font-medium">{{ 'analytics.inventory.col.risk' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (item of visibleCritical(); track item.productId) {
                  <tr class="border-t border-[var(--border-subtle)] transition-colors hover:bg-[var(--bg-tertiary)]"
                      style="height: 40px">
                    <td class="whitespace-nowrap px-2.5 font-mono text-[11px] text-[var(--text-secondary)]"
                        [title]="item.skuCode">
                      {{ item.skuCode }}
                    </td>
                    <td class="max-w-[240px] truncate px-2.5 text-[var(--text-primary)]"
                        [title]="item.productName">
                      {{ item.productName }}
                    </td>
                    <td class="whitespace-nowrap px-2.5 text-xs text-[var(--text-secondary)]">
                      {{ item.sourcePlatform }}
                    </td>
                    <td class="whitespace-nowrap px-2.5 text-right font-mono text-[var(--text-primary)]">
                      {{ item.available }}
                    </td>
                    <td class="whitespace-nowrap px-2.5 text-right font-mono text-[var(--text-primary)]">
                      {{ item.daysOfCover ?? '—' }}
                    </td>
                    <td class="px-2.5">
                      <dp-stock-risk-badge [risk]="item.stockOutRisk" />
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          @if (canExpandTable()) {
            <button
              type="button"
              (click)="tableExpanded.set(!tableExpanded())"
              class="mt-2 w-full rounded-[var(--radius-md)] py-1.5 text-[length:var(--text-sm)]
                     font-medium text-[var(--accent-primary)] transition-colors
                     hover:bg-[var(--bg-secondary)]"
            >
              @if (tableExpanded()) {
                {{ 'common.collapse' | translate }}
              } @else {
                {{ 'analytics.inventory.show_all_critical' | translate:{ count: topCritical().length } }}
              }
            </button>
          }
        }
      </div>
    </div>
  `,
})
export class InventoryOverviewPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly t = inject(TranslateService);

  protected readonly PackageIcon = Package;
  protected readonly AlertTriangleIcon = AlertTriangle;
  protected readonly AlertCircleIcon = AlertCircle;
  protected readonly DollarSignIcon = DollarSign;

  readonly dateFrom = signal(daysAgo(30));
  readonly dateTo = signal(daysAgo(0));

  readonly overviewQuery = injectQuery(() => ({
    queryKey: ['analytics', 'inventory-overview', this.wsStore.currentWorkspaceId()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getInventoryOverview(
          this.wsStore.currentWorkspaceId()!,
          {},
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly dynamicsQuery = injectQuery(() => ({
    queryKey: [
      'analytics', 'inventory-dynamics',
      this.wsStore.currentWorkspaceId(),
      this.dateFrom(),
      this.dateTo(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getStockHistory(
          this.wsStore.currentWorkspaceId()!,
          this.dateFrom(),
          this.dateTo(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId()
      && !!this.dateFrom()
      && !!this.dateTo(),
    staleTime: 60_000,
  }));

  readonly overview = computed(() => this.overviewQuery.data() ?? null);

  readonly kpiTotalSkus = computed(() => {
    const data = this.overview();
    return data ? data.totalSkus.toLocaleString('ru-RU') : null;
  });

  readonly kpiCritical = computed(() => {
    const data = this.overview();
    return data ? data.criticalCount.toLocaleString('ru-RU') : null;
  });

  readonly kpiWarning = computed(() => {
    const data = this.overview();
    return data ? data.warningCount.toLocaleString('ru-RU') : null;
  });

  readonly kpiFrozenCapital = computed(() => {
    const data = this.overview();
    return data ? formatMoney(data.frozenCapital, 0) : null;
  });

  readonly topCritical = computed<InventoryByProduct[]>(() =>
    this.overview()?.topCritical ?? [],
  );

  readonly tableExpanded = signal(false);

  readonly canExpandTable = computed(() =>
    this.topCritical().length > COLLAPSED_ROW_LIMIT,
  );

  readonly visibleCritical = computed<InventoryByProduct[]>(() => {
    const all = this.topCritical();
    if (this.tableExpanded() || all.length <= COLLAPSED_ROW_LIMIT) {
      return all;
    }
    return all.slice(0, COLLAPSED_ROW_LIMIT);
  });

  readonly dynamicsKpi = computed(() => {
    const points = this.dynamicsQuery.data() ?? [];
    if (points.length === 0) {
      return { lastAvailable: null, lastReserved: null, deltaPct: null };
    }
    const first = points[0];
    const last = points[points.length - 1];
    return {
      lastAvailable: last.available,
      lastReserved: last.reserved ?? 0,
      deltaPct: pctDelta(first.available, last.available),
    };
  });

  readonly riskChartOptions = computed<EChartsOption>(() => {
    const data = this.overview();
    const successColor = resolveCssVar('--status-success');
    const warningColor = resolveCssVar('--status-warning');
    const errorColor = resolveCssVar('--status-error');

    return {
      grid: { left: 80, right: 40, top: 8, bottom: 8 },
      xAxis: { type: 'value', show: false },
      yAxis: {
        type: 'category',
        data: ['NORMAL', 'WARNING', 'CRITICAL'],
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          formatter: (v: string) => this.t.instant(`analytics.inventory.risk.${v.toLowerCase()}`),
          color: resolveCssVar('--text-secondary'),
          fontSize: 12,
        },
      },
      series: [
        {
          type: 'bar',
          data: [
            {
              value: data?.normalCount ?? 0,
              itemStyle: { color: successColor, borderRadius: [0, 4, 4, 0] },
            },
            {
              value: data?.warningCount ?? 0,
              itemStyle: { color: warningColor, borderRadius: [0, 4, 4, 0] },
            },
            {
              value: data?.criticalCount ?? 0,
              itemStyle: { color: errorColor, borderRadius: [0, 4, 4, 0] },
            },
          ],
          barWidth: 20,
          label: {
            show: true,
            position: 'right',
            color: resolveCssVar('--text-secondary'),
            fontSize: 12,
          },
        },
      ],
      tooltip: { show: false },
    };
  });

  readonly dynamicsChartOptions = computed<EChartsOption>(() => {
    const points = this.dynamicsQuery.data() ?? [];
    const dates = points.map((p) => p.date);
    const availableData = points.map((p) => p.available);
    const reservedData = points.map((p) => p.reserved ?? 0);

    const accentColor = resolveCssVar('--accent-primary');
    const warningColor = resolveCssVar('--status-warning');
    const accentSubtle = resolveCssVar('--accent-subtle');

    return {
      grid: { left: 50, right: 20, top: 20, bottom: 30 },
      xAxis: {
        type: 'category',
        data: dates,
        axisLabel: { color: resolveCssVar('--text-secondary'), fontSize: 11 },
        axisLine: { lineStyle: { color: resolveCssVar('--border-default') } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: resolveCssVar('--text-secondary'), fontSize: 11 },
        splitLine: { lineStyle: { color: resolveCssVar('--border-subtle') } },
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: resolveCssVar('--bg-primary'),
        borderColor: resolveCssVar('--border-default'),
        textStyle: { color: resolveCssVar('--text-primary'), fontSize: 12 },
      },
      legend: {
        data: [
          this.t.instant('analytics.inventory.chart.available'),
          this.t.instant('analytics.inventory.chart.reserved'),
        ],
        top: 0,
        right: 0,
        textStyle: { color: resolveCssVar('--text-secondary'), fontSize: 12 },
      },
      series: [
        {
          name: this.t.instant('analytics.inventory.chart.available'),
          type: 'line',
          step: 'end',
          data: availableData,
          lineStyle: { color: accentColor, width: 2 },
          itemStyle: { color: accentColor },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: accentSubtle },
                { offset: 1, color: 'transparent' },
              ],
            } as Record<string, unknown>,
          },
          symbol: 'none',
        },
        {
          name: this.t.instant('analytics.inventory.chart.reserved'),
          type: 'line',
          step: 'end',
          data: reservedData,
          lineStyle: { color: warningColor, width: 2, type: 'dashed' },
          itemStyle: { color: warningColor },
          symbol: 'none',
        },
      ],
    };
  });
}
