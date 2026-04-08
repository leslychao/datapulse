import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import type { EChartsOption } from 'echarts';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { Granularity, PnlTrendPoint } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { DateRangePickerComponent } from '@shared/components/form/date-range-picker.component';
import { formatMoney } from '@shared/utils/format.utils';
import {
  UrlFilterDef, isFiltersDefault, resetFilters, initPersistedFilters,
} from '@shared/utils/url-filters';

function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

const GRANULARITY_OPTIONS: { value: Granularity; labelKey: string }[] = [
  { value: 'DAILY', labelKey: 'analytics.pnl.granularity.daily' },
  { value: 'WEEKLY', labelKey: 'analytics.pnl.granularity.weekly' },
  { value: 'MONTHLY', labelKey: 'analytics.pnl.granularity.monthly' },
];

@Component({
  selector: 'dp-pnl-trend-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, ChartComponent, DateRangePickerComponent],
  template: `
    <div class="flex flex-col gap-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3">
        <dp-date-range-picker
          [from]="dateFrom()"
          [to]="dateTo()"
          (fromChange)="dateFrom.set($event)"
          (toChange)="dateTo.set($event)"
        />

        @if (!filtersDefault()) {
          <button type="button" (click)="onResetFilters()"
            class="h-8 cursor-pointer rounded-[var(--radius-md)] px-3 text-[length:var(--text-sm)]
                   text-[var(--text-tertiary)] transition-colors
                   hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]">
            {{ 'filter_bar.reset_all' | translate }}
          </button>
        }

        <!-- Granularity switcher -->
        <div class="ml-auto flex rounded-[var(--radius-md)] border border-[var(--border-default)]">
          @for (opt of granularityOptions; track opt.value) {
            <button
              (click)="granularity.set(opt.value)"
              class="px-3 py-1 text-[length:var(--text-sm)] transition-colors first:rounded-l-[var(--radius-md)]
                     last:rounded-r-[var(--radius-md)]"
              [class]="granularity() === opt.value
                ? 'bg-[var(--accent-primary)] text-white'
                : 'bg-[var(--bg-primary)] text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]'"
            >
              {{ opt.labelKey | translate }}
            </button>
          }
        </div>
      </div>

      <!-- Chart -->
      <div class="rounded-[var(--radius-md)] bg-[var(--bg-primary)] p-4 shadow-[var(--shadow-sm)]">
        <dp-chart
          [options]="chartOptions()"
          height="400px"
          [loading]="trendQuery.isPending()"
        />
      </div>

      <!-- Summary table -->
      @if (trendQuery.data(); as points) {
        @if (points.length > 0) {
          <div class="overflow-x-auto rounded-[var(--radius-md)] border border-[var(--border-default)]">
            <table class="w-full text-left text-[length:var(--text-sm)]">
              <thead class="bg-[var(--bg-secondary)] text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                <tr>
                  <th class="px-3 py-2 font-medium">{{ 'analytics.pnl.trend.period' | translate }}</th>
                  <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.revenue' | translate }}</th>
                  <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.kpi.total_costs' | translate }}</th>
                  <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.kpi.cogs' | translate }}</th>
                  <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.kpi.advertising' | translate }}</th>
                  <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.full_pnl' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (pt of points; track pt.period) {
                  <tr class="border-t border-[var(--border-subtle)]">
                    <td class="px-3 py-2 text-[var(--text-secondary)]">{{ pt.period }}</td>
                    <td class="px-3 py-2 text-right font-mono">{{ formatMoney(pt.revenueAmount) }}</td>
                    <td class="px-3 py-2 text-right font-mono text-[var(--finance-negative)]">
                      {{ formatMoney(pt.totalCostsAmount) }}
                    </td>
                    <td class="px-3 py-2 text-right font-mono text-[var(--finance-negative)]">{{ formatMoney(pt.cogsAmount) }}</td>
                    <td class="px-3 py-2 text-right font-mono text-[var(--finance-negative)]">{{ formatMoney(pt.advertisingCostAmount) }}</td>
                    <td class="px-3 py-2 text-right font-mono font-semibold" [class]="moneyColorClass(pt.fullPnl)">
                      {{ formatMoney(pt.fullPnl) }}
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }
    </div>
  `,
})
export class PnlTrendPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly t = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly dateFrom = signal(daysAgo(90));
  readonly dateTo = signal(daysAgo(0));
  readonly granularity = signal<Granularity>('MONTHLY');
  readonly granularityOptions = GRANULARITY_OPTIONS;

  private readonly filterDefs: UrlFilterDef[] = [
    { key: 'from', signal: this.dateFrom, defaultValue: daysAgo(90) },
    { key: 'to', signal: this.dateTo, defaultValue: daysAgo(0) },
    { key: 'granularity', signal: this.granularity as any, defaultValue: 'MONTHLY' },
  ];
  readonly filtersDefault = isFiltersDefault(this.filterDefs);

  constructor() {
    initPersistedFilters(this.router, this.route, {
      pageKey: 'analytics:pnl:trend', filterDefs: this.filterDefs,
    });
  }

  onResetFilters(): void {
    resetFilters(this.filterDefs);
  }

  readonly trendQuery = injectQuery(() => ({
    queryKey: [
      'analytics', 'pnl-trend-page',
      this.wsStore.currentWorkspaceId(),
      this.dateFrom(),
      this.dateTo(),
      this.granularity(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getPnlTrend(this.wsStore.currentWorkspaceId()!, {
          from: this.dateFrom(),
          to: this.dateTo(),
          granularity: this.granularity(),
        }),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly chartOptions = computed<EChartsOption>(() => {
    const points = this.trendQuery.data() ?? [];
    const periods = points.map((p) => p.period);
    return {
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0, textStyle: { fontSize: 11 } },
      grid: { top: 16, right: 16, bottom: 40, left: 60 },
      xAxis: {
        type: 'category',
        data: periods,
        axisLabel: { fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        axisLabel: { fontSize: 11 },
      },
      series: [
        {
          name: this.t.instant('analytics.pnl.chart.revenue'),
          type: 'line',
          data: points.map((p) => p.revenueAmount),
          smooth: true,
          itemStyle: { color: '#059669' },
          lineStyle: { width: 2 },
          areaStyle: { color: 'rgba(5,150,105,0.1)' },
        },
        {
          name: this.t.instant('analytics.pnl.chart.costs'),
          type: 'line',
          data: points.map((p) => p.totalCostsAmount),
          smooth: true,
          itemStyle: { color: '#DC2626' },
          lineStyle: { width: 2, type: 'dashed' },
        },
        {
          name: this.t.instant('analytics.pnl.chart.pnl'),
          type: 'line',
          data: points.map((p) => p.fullPnl),
          smooth: true,
          itemStyle: { color: '#2563EB' },
          lineStyle: { width: 3 },
        },
      ],
    };
  });

  formatMoney(value: number | null): string {
    return formatMoney(value, 0);
  }

  moneyColorClass(value: number | null): string {
    if (value != null && value > 0) return 'text-[var(--finance-positive)]';
    if (value != null && value < 0) return 'text-[var(--finance-negative)]';
    return 'text-[var(--finance-zero)]';
  }
}
