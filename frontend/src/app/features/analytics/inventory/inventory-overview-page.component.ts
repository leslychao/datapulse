import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import type { EChartsOption } from 'echarts';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { InventoryByProduct } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { StockRiskBadgeComponent } from '@shared/components/stock-risk-badge.component';
import { formatMoney } from '@shared/utils/format.utils';

const COLLAPSED_ROW_LIMIT = 5;
const MIN_COL_WIDTH = 50;

function resolveCssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || name;
}

@Component({
  selector: 'dp-inventory-overview-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, ChartComponent, StockRiskBadgeComponent],
  styles: [`
    .dp-col-resize {
      position: absolute;
      right: -2px;
      top: 0;
      width: 5px;
      height: 100%;
      cursor: col-resize;
      z-index: 1;
      background: transparent;
      transition: background-color 0.15s;
    }
    .dp-col-resize:hover {
      background-color: var(--accent-primary);
    }
  `],
  template: `
    <div class="flex flex-col gap-4">
      <!-- KPI cards -->
      <div class="grid grid-cols-3 gap-3">
        <!-- Total SKUs -->
        <div
          class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4"
        >
          <p class="text-xs text-[var(--text-secondary)]">
            {{ 'analytics.inventory.kpi.total_skus' | translate }}
          </p>
          @if (overviewQuery.isPending()) {
            <div class="dp-shimmer mt-1 h-7 w-20 rounded"></div>
          } @else {
            <p class="mt-1 font-mono text-2xl font-semibold text-[var(--text-primary)]">
              {{ overview()?.totalSkus?.toLocaleString('ru-RU') ?? '—' }}
            </p>
          }
        </div>

        <!-- Critical count -->
        <div
          class="rounded-[var(--radius-lg)] border bg-[var(--bg-primary)] p-4"
          [class]="criticalCount() > 0
            ? 'border-[var(--status-error)]'
            : 'border-[var(--border-default)]'"
        >
          <p class="text-xs text-[var(--text-secondary)]">
            {{ 'analytics.inventory.kpi.critical' | translate }}
          </p>
          @if (overviewQuery.isPending()) {
            <div class="dp-shimmer mt-1 h-7 w-14 rounded"></div>
          } @else {
            <p
              class="mt-1 font-mono text-2xl font-semibold"
              [class]="criticalCount() > 0
                ? 'text-[var(--status-error)]'
                : 'text-[var(--text-primary)]'"
            >
              {{ criticalCount() }}
            </p>
          }
        </div>

        <!-- Frozen Capital -->
        <div
          class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4"
        >
          <p class="text-xs text-[var(--text-secondary)]">
            {{ 'analytics.inventory.kpi.frozen_capital' | translate }}
          </p>
          @if (overviewQuery.isPending()) {
            <div class="dp-shimmer mt-1 h-7 w-28 rounded"></div>
          } @else {
            <p class="mt-1 font-mono text-2xl font-semibold text-[var(--text-primary)]">
              {{ formatMoney(overview()?.frozenCapital ?? null) }}
            </p>
          }
        </div>
      </div>

      <!-- Risk distribution chart -->
      <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
        <p class="mb-2 text-sm font-medium text-[var(--text-primary)]">
          {{ 'analytics.inventory.risk_distribution' | translate }}
        </p>
        <dp-chart
          [options]="riskChartOptions()"
          [loading]="overviewQuery.isPending()"
          height="120px"
        />
      </div>

      <!-- Top-10 critical products -->
      <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
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
          <div class="overflow-x-auto">
            <table class="w-full table-fixed text-left text-sm">
              <colgroup>
                <col [style.width.px]="colWidths()[0]" />
                <col />
                <col [style.width.px]="colWidths()[2]" />
                <col [style.width.px]="colWidths()[3]" />
                <col [style.width.px]="colWidths()[4]" />
              </colgroup>
              <thead>
                <tr class="border-b border-[var(--border-subtle)] text-xs text-[var(--text-secondary)]">
                  <th class="relative truncate pb-2 pr-4 font-medium">
                    SKU
                    <div class="dp-col-resize" (mousedown)="onColResize($event, 0)"></div>
                  </th>
                  <th class="relative truncate pb-2 pr-4 font-medium">
                    {{ 'analytics.inventory.col.product' | translate }}
                  </th>
                  <th class="relative truncate pb-2 pr-4 text-right font-medium">
                    {{ 'analytics.inventory.col.available' | translate }}
                    <div class="dp-col-resize" (mousedown)="onColResize($event, 2)"></div>
                  </th>
                  <th class="relative truncate pb-2 pr-4 text-right font-medium">
                    {{ 'analytics.inventory.col.days_of_cover' | translate }}
                    <div class="dp-col-resize" (mousedown)="onColResize($event, 3)"></div>
                  </th>
                  <th class="relative truncate pb-2 font-medium">
                    {{ 'analytics.inventory.col.risk' | translate }}
                  </th>
                </tr>
              </thead>
              <tbody>
                @for (item of visibleCritical(); track item.productId) {
                  <tr class="border-b border-[var(--border-subtle)] last:border-0">
                    <td class="truncate py-2 pr-4 font-mono text-xs text-[var(--text-secondary)]"
                        [title]="item.skuCode">
                      {{ item.skuCode }}
                    </td>
                    <td class="truncate py-2 pr-4 text-[var(--text-primary)]"
                        [title]="item.productName">
                      {{ item.productName }}
                    </td>
                    <td class="truncate py-2 pr-4 text-right font-mono">
                      {{ item.available }}
                    </td>
                    <td class="truncate py-2 pr-4 text-right font-mono">
                      {{ item.daysOfCover ?? '—' }}
                    </td>
                    <td class="truncate py-2">
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

  readonly overview = computed(() => this.overviewQuery.data() ?? null);

  readonly criticalCount = computed(() => this.overview()?.criticalCount ?? 0);

  readonly topCritical = computed<InventoryByProduct[]>(() =>
    this.overview()?.topCritical ?? [],
  );

  readonly tableExpanded = signal(false);
  readonly colWidths = signal([180, 0, 80, 100, 90]);

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

  onColResize(event: MouseEvent, col: number): void {
    event.preventDefault();
    const startX = event.clientX;
    const startW = this.colWidths()[col];

    const onMove = (e: MouseEvent) => {
      const w = Math.max(MIN_COL_WIDTH, startW + e.clientX - startX);
      this.colWidths.update(ws => {
        const next = [...ws];
        next[col] = w;
        return next;
      });
    };

    const onUp = () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };

    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  }

  formatMoney(value: number | null): string {
    return formatMoney(value, 0);
  }
}
