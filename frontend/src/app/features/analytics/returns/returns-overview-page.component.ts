import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import type { EChartsOption } from 'echarts';
import { LucideAngularModule, Info } from 'lucide-angular';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { MARKETPLACE_REGISTRY } from '@core/models';
import { NavigationStore } from '@shared/stores/navigation.store';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { MonthPickerComponent } from '@shared/components/form/month-picker.component';
import { formatMoney, formatPercent, currentMonth } from '@shared/utils/format.utils';
import {
  UrlFilterDef, isFiltersDefault, resetFilters, initPersistedFilters,
} from '@shared/utils/url-filters';

type TrendMetric = 'rate' | 'count';

function monthStart(period: string): string {
  return `${period}-01`;
}

function monthEnd(period: string): string {
  const [y, m] = period.split('-').map(Number);
  const last = new Date(y, m, 0).getDate();
  return `${period}-${String(last).padStart(2, '0')}`;
}

@Component({
  selector: 'dp-returns-overview-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, ChartComponent, MonthPickerComponent, LucideAngularModule, RouterLink],
  template: `
    <div class="flex h-full flex-col gap-4 pb-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3">
        <dp-month-picker [value]="period()" (valueChange)="period.set($event)" />
        <select
          [value]="platform()"
          (change)="platform.set($any($event.target).value)"
          class="h-8 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]
                 px-2.5 text-[length:var(--text-sm)] text-[var(--text-primary)]
                 outline-none focus:border-[var(--accent-primary)]">
          <option value="">{{ 'analytics.returns.filter.all_platforms' | translate }}</option>
          @for (mp of marketplaces; track mp.type) {
            <option [value]="mp.type">{{ mp.label }}</option>
          }
        </select>
        @if (!filtersDefault()) {
          <button type="button" (click)="onResetFilters()"
            class="h-8 cursor-pointer rounded-[var(--radius-md)] px-3 text-[length:var(--text-sm)]
                   text-[var(--text-tertiary)] transition-colors
                   hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]">
            {{ 'filter_bar.reset_all' | translate }}
          </button>
        }
      </div>

      <!-- Info banner -->
      <div class="flex items-start gap-2.5 rounded-[var(--radius-md)] border border-[var(--accent-subtle)]
                  bg-[color-mix(in_srgb,var(--accent-primary)_5%,transparent)] px-4 py-3">
        <lucide-icon [img]="infoIcon" size="16"
          class="mt-0.5 shrink-0 text-[var(--accent-primary)]" />
        <div>
          <span class="text-[length:var(--text-sm)] font-medium text-[var(--text-primary)]">
            {{ 'analytics.returns.info_banner.title' | translate }}
          </span>
          <p class="mt-0.5 text-[length:var(--text-xs)] text-[var(--text-secondary)]">
            {{ 'analytics.returns.info_banner.description' | translate }}
          </p>
        </div>
      </div>

      @if (summaryQuery.isPending()) {
        <div class="grid grid-cols-4 gap-3">
          @for (_ of [1, 2, 3, 4]; track $index) {
            <div class="dp-shimmer h-[88px] rounded-[var(--radius-md)]"></div>
          }
        </div>
      }

      @if (summaryQuery.data(); as s) {
        @if (s.totalReturnCount === 0) {
          <div class="rounded-[var(--radius-md)] border border-[var(--border-default)]
                      bg-[var(--bg-secondary)] px-4 py-8 text-center">
            <p class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'analytics.returns.empty_operational_month' | translate }}
            </p>
          </div>
        } @else {
          <!-- KPI Cards -->
          <div class="grid grid-cols-4 gap-3">
            <!-- Return Rate -->
            <div class="flex flex-col gap-1 rounded-[var(--radius-md)] border border-[var(--border-default)]
                        bg-[var(--bg-primary)] p-3 shadow-[var(--shadow-sm)]">
              <span class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                {{ 'analytics.returns.kpi.return_rate' | translate }}
              </span>
              <span class="font-mono text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]">
                {{ formatPct(s.returnRatePct) }}
              </span>
              @if (s.returnRateDeltaPct != null) {
                <span class="font-mono text-[length:var(--text-xs)]"
                      [class]="deltaColorClass(s.returnRateDeltaPct)">
                  {{ formatDeltaText(s.returnRateDeltaPct) }}
                </span>
              }
            </div>

            <!-- Return Count -->
            <div class="flex flex-col gap-1 rounded-[var(--radius-md)] border border-[var(--border-default)]
                        bg-[var(--bg-primary)] p-3 shadow-[var(--shadow-sm)]">
              <span class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                {{ 'analytics.returns.kpi.return_count' | translate }}
              </span>
              <span class="font-mono text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]">
                {{ s.totalReturnCount.toLocaleString('ru-RU') }}
              </span>
            </div>

            <!-- Return Amount -->
            <div class="flex flex-col gap-1 rounded-[var(--radius-md)] border border-[var(--border-default)]
                        bg-[var(--bg-primary)] p-3 shadow-[var(--shadow-sm)]">
              <span class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                {{ 'analytics.returns.kpi.return_amount' | translate }}
              </span>
              <span class="font-mono text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]">
                {{ formatMoney(s.totalReturnAmount) }}
              </span>
            </div>

            <!-- Products with Returns -->
            <div class="flex flex-col gap-1 rounded-[var(--radius-md)] border border-[var(--border-default)]
                        bg-[var(--bg-primary)] p-3 shadow-[var(--shadow-sm)]">
              <span class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                {{ 'analytics.returns.kpi.products_with_returns' | translate }}
              </span>
              <span class="font-mono text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]">
                {{ s.productsWithReturnsCount }}
              </span>
            </div>
          </div>

          <!-- Two-column: Trend + Reasons -->
          <div class="grid grid-cols-2 gap-4">
            <!-- Trend block -->
            <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
              <div class="mb-3 flex items-center justify-between">
                <h3 class="text-sm font-medium text-[var(--text-primary)]">
                  {{ 'analytics.returns.trend_title' | translate }}
                </h3>
                <div class="flex gap-1">
                  @for (opt of trendMetricOptions; track opt.value) {
                    <button
                      (click)="trendMetric.set(opt.value)"
                      class="cursor-pointer rounded-[var(--radius-sm)] px-2.5 py-1 text-xs transition-colors"
                      [class]="trendMetric() === opt.value
                        ? 'bg-[var(--accent-primary)] text-white'
                        : 'bg-[var(--bg-secondary)] text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]'"
                    >
                      {{ opt.labelKey | translate }}
                    </button>
                  }
                </div>
              </div>
              @if (trendInsight()) {
                <p class="mb-2 text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                  {{ trendInsight() }}
                </p>
              }
              <dp-chart
                [options]="trendChartOptions()"
                [loading]="trendQuery.isPending()"
                height="200px"
              />
            </div>

            <!-- Reasons block -->
            <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
              <div class="mb-3 flex items-center justify-between">
                <h3 class="text-sm font-medium text-[var(--text-primary)]">
                  {{ 'analytics.returns.reasons_chart_title' | translate }}
                </h3>
                @if (s.topReturnReason) {
                  <span class="rounded-[var(--radius-sm)] bg-[var(--bg-tertiary)] px-2 py-0.5
                               text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                    {{ 'analytics.returns.kpi.top_reason' | translate }}: {{ s.topReturnReason }}
                  </span>
                }
              </div>
              <dp-chart
                [options]="reasonChartOptions()"
                [loading]="summaryQuery.isPending()"
                height="200px"
              />
            </div>
          </div>

          <!-- Problem products -->
          <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
            <div class="mb-3 flex items-center justify-between">
              <h3 class="text-sm font-medium text-[var(--text-primary)]">
                {{ 'analytics.returns.problem_products_title' | translate }}
              </h3>
              <a [routerLink]="['../by-product']"
                class="text-[length:var(--text-sm)] font-medium text-[var(--accent-primary)]
                       transition-colors hover:text-[var(--accent-primary-hover)]">
                {{ 'analytics.returns.view_all_products' | translate }}
              </a>
            </div>
            @if (topProducts().length === 0) {
              <p class="py-4 text-center text-[length:var(--text-sm)] text-[var(--text-tertiary)]">
                {{ 'analytics.returns.no_problem_products' | translate }}
              </p>
            } @else {
              <div class="dp-table-wrap">
                <table class="dp-table">
                  <thead>
                    <tr>
                      <th class="text-[length:var(--text-xs)] uppercase tracking-wider text-[var(--text-tertiary)]">SKU</th>
                      <th class="text-[length:var(--text-xs)] uppercase tracking-wider text-[var(--text-tertiary)]">{{ 'analytics.returns.col.product' | translate }}</th>
                      <th class="text-right text-[length:var(--text-xs)] uppercase tracking-wider text-[var(--text-tertiary)]">{{ 'analytics.returns.col.return_rate' | translate }}</th>
                      <th class="text-right text-[length:var(--text-xs)] uppercase tracking-wider text-[var(--text-tertiary)]">{{ 'analytics.returns.col.return_count' | translate }}</th>
                      <th class="text-[length:var(--text-xs)] uppercase tracking-wider text-[var(--text-tertiary)]">{{ 'analytics.returns.col.top_reason' | translate }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (item of topProducts(); track item.sellerSkuId) {
                      <tr>
                        <td class="whitespace-nowrap font-mono text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                          {{ item.skuCode || '—' }}
                        </td>
                        <td class="max-w-[200px] truncate text-[var(--text-primary)]"
                            [title]="item.productName || ''">
                          {{ item.productName || '—' }}
                        </td>
                        <td class="whitespace-nowrap text-right font-mono"
                            [class]="returnRateClass(item.returnRatePct)">
                          {{ formatPct(item.returnRatePct) }}
                        </td>
                        <td class="whitespace-nowrap text-right font-mono text-[var(--text-primary)]">
                          {{ item.returnCount.toLocaleString('ru-RU') }}
                        </td>
                        <td class="text-[var(--text-secondary)]">
                          {{ item.topReturnReason || '—' }}
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        }
      }
    </div>
  `,
})
export class ReturnsOverviewPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly navStore = inject(NavigationStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly t = inject(TranslateService);

  readonly infoIcon = Info;
  protected readonly marketplaces = MARKETPLACE_REGISTRY;

  readonly period = signal(
    this.navStore.getSectionFilterValue<string>('analytics:returns', 'period') ?? currentMonth(),
  );
  readonly platform = signal(
    this.navStore.getSectionFilterValue<string>('analytics:returns', 'platform') ?? '',
  );

  readonly trendMetric = signal<TrendMetric>('rate');
  readonly trendMetricOptions: { value: TrendMetric; labelKey: string }[] = [
    { value: 'rate', labelKey: 'analytics.returns.metric.rate' },
    { value: 'count', labelKey: 'analytics.returns.metric.count' },
  ];

  private readonly filterDefs: UrlFilterDef[] = [
    { key: 'period', signal: this.period, defaultValue: currentMonth() },
    { key: 'platform', signal: this.platform, defaultValue: '' },
  ];
  readonly filtersDefault = isFiltersDefault(this.filterDefs);

  constructor() {
    initPersistedFilters(this.router, this.route, {
      pageKey: 'analytics:returns:overview', filterDefs: this.filterDefs,
    });
    effect(() => {
      const period = this.period();
      const platform = this.platform();
      this.navStore.setSectionFilter('analytics:returns', {
        period, platform,
        from: monthStart(period),
        to: monthEnd(period),
      });
    });
  }

  onResetFilters(): void {
    resetFilters(this.filterDefs);
  }

  readonly summaryQuery = injectQuery(() => ({
    queryKey: ['analytics', 'returns-summary', this.wsStore.currentWorkspaceId(), this.period(), this.platform()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getReturnsSummary(
          this.wsStore.currentWorkspaceId()!,
          { period: this.period(), sourcePlatform: this.platform() || undefined },
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly trendQuery = injectQuery(() => ({
    queryKey: ['analytics', 'returns-trend', this.wsStore.currentWorkspaceId(), this.period(), this.platform()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getReturnsTrend(
          this.wsStore.currentWorkspaceId()!,
          { from: monthStart(this.period()), to: monthEnd(this.period()), granularity: 'DAILY',
            sourcePlatform: this.platform() || undefined },
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly topProductsQuery = injectQuery(() => ({
    queryKey: ['analytics', 'returns-top-products', this.wsStore.currentWorkspaceId(), this.period(), this.platform()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.listReturnsByProduct(
          this.wsStore.currentWorkspaceId()!,
          { period: this.period(), sourcePlatform: this.platform() || undefined },
          0, 5, 'returnRatePct,desc',
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly topProducts = computed(() => this.topProductsQuery.data()?.content ?? []);

  readonly reasonChartOptions = computed<EChartsOption>(() => {
    const items = this.summaryQuery.data()?.reasonBreakdown ?? [];
    return {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: any) => {
          const p = Array.isArray(params) ? params[0] : params;
          const item = items[p.dataIndex];
          if (!item) return '';
          return `<b>${item.reason}</b><br/>${item.count} (${item.percent}%)`;
        },
      },
      grid: { left: 120, right: 50, top: 8, bottom: 8 },
      xAxis: { type: 'value', show: false },
      yAxis: {
        type: 'category',
        data: items.map((i) => i.reason),
        inverse: true,
        axisLabel: {
          width: 110,
          overflow: 'truncate',
          color: 'var(--text-secondary)',
          fontSize: 11,
        },
        axisTick: { show: false },
        axisLine: { show: false },
      },
      series: [
        {
          type: 'bar',
          data: items.map((i) => i.count),
          barMaxWidth: 18,
          itemStyle: {
            color: 'var(--accent-primary)',
            borderRadius: [0, 4, 4, 0],
          },
          label: {
            show: true,
            position: 'right',
            formatter: (p: any) => {
              const item = items[p.dataIndex];
              return item ? `${item.percent}%` : '';
            },
            fontSize: 11,
            color: 'var(--text-secondary)',
          },
        },
      ],
    };
  });

  readonly trendChartOptions = computed<EChartsOption>(() => {
    const data = this.trendQuery.data() ?? [];
    const periods = data.map((d) => d.period);
    const metric = this.trendMetric();

    if (metric === 'rate') {
      return {
        tooltip: {
          trigger: 'axis',
          formatter: (params: any) => {
            const p = Array.isArray(params) ? params[0] : params;
            return `${p.name}: ${p.value ?? '—'}%`;
          },
        },
        grid: { left: 50, right: 16, top: 8, bottom: 24 },
        xAxis: {
          type: 'category',
          data: periods,
          axisLabel: { color: 'var(--text-tertiary)', fontSize: 10, rotate: 45 },
          axisTick: { show: false },
          axisLine: { lineStyle: { color: 'var(--border-default)' } },
        },
        yAxis: {
          type: 'value',
          axisLabel: { formatter: '{value}%', color: 'var(--text-tertiary)', fontSize: 10 },
          splitLine: { lineStyle: { color: 'var(--border-subtle)', type: 'dashed' } },
        },
        series: [{
          type: 'line',
          data: data.map((d) => d.returnRatePct),
          smooth: true,
          symbol: 'circle',
          symbolSize: 4,
          lineStyle: { color: 'var(--status-error)', width: 2 },
          itemStyle: { color: 'var(--status-error)' },
          areaStyle: { color: 'rgba(239, 68, 68, 0.08)' },
        }],
      };
    }

    return {
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 16, top: 8, bottom: 24 },
      xAxis: {
        type: 'category',
        data: periods,
        axisLabel: { color: 'var(--text-tertiary)', fontSize: 10, rotate: 45 },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: 'var(--border-default)' } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: 'var(--text-tertiary)', fontSize: 10 },
        splitLine: { lineStyle: { color: 'var(--border-subtle)', type: 'dashed' } },
      },
      series: [{
        type: 'bar',
        data: data.map((d) => d.returnQuantity),
        barMaxWidth: 16,
        itemStyle: { color: 'var(--status-error)', borderRadius: [4, 4, 0, 0] },
      }],
    };
  });

  readonly trendInsight = computed<string | null>(() => {
    const data = this.trendQuery.data();
    if (!data || data.length < 2) return null;
    const last = data[data.length - 1];
    const prev = data[data.length - 2];
    if (last.returnRatePct == null || prev.returnRatePct == null) return null;
    const diff = last.returnRatePct - prev.returnRatePct;
    if (Math.abs(diff) < 0.1) return null;
    const key = diff > 0
      ? 'analytics.returns.trend_insight.up'
      : 'analytics.returns.trend_insight.down';
    return this.t.instant(key, { value: formatPercent(Math.abs(diff)) });
  });

  returnRateClass(rate: number): string {
    if (rate > 10) return 'text-[var(--status-error)]';
    if (rate >= 5) return 'text-[var(--status-warning)]';
    return 'text-[var(--text-primary)]';
  }

  formatMoney(value: number | null): string {
    return formatMoney(value, 0);
  }

  formatPct(value: number | null): string {
    return formatPercent(value);
  }

  formatDeltaText(value: number): string {
    if (value > 0) return `↑ ${formatPercent(value)}`;
    if (value < 0) return `↓ ${formatPercent(Math.abs(value))}`;
    return `→ ${formatPercent(0)}`;
  }

  deltaColorClass(value: number): string {
    if (value > 0) return 'text-[var(--status-error)]';
    if (value < 0) return 'text-[var(--status-success)]';
    return 'text-[var(--text-secondary)]';
  }
}
