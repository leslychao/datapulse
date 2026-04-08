import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import type { EChartsOption } from 'echarts';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { AnalyticsFilter, Granularity } from '@core/models';
import { NavigationStore } from '@shared/stores/navigation.store';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { DateRangePickerComponent } from '@shared/components/form/date-range-picker.component';
import {
  UrlFilterDef, isFiltersDefault, resetFilters, initPersistedFilters,
} from '@shared/utils/url-filters';

function monthStart(period: string): string {
  return `${period}-01`;
}

function monthEnd(period: string): string {
  const [y, m] = period.split('-').map(Number);
  const last = new Date(y, m, 0).getDate();
  return `${period}-${String(last).padStart(2, '0')}`;
}

function inferMonthPeriod(from: string, to: string): string | null {
  const fromPeriod = from.slice(0, 7);
  const toPeriod = to.slice(0, 7);
  if (fromPeriod !== toPeriod) {
    return null;
  }
  return from === monthStart(fromPeriod) && to === monthEnd(fromPeriod)
    ? fromPeriod
    : null;
}

const GRANULARITY_OPTIONS: { value: Granularity; labelKey: string }[] = [
  { value: 'DAILY', labelKey: 'analytics.returns.granularity.daily' },
  { value: 'WEEKLY', labelKey: 'analytics.returns.granularity.weekly' },
  { value: 'MONTHLY', labelKey: 'analytics.returns.granularity.monthly' },
];

@Component({
  selector: 'dp-returns-trend-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, ChartComponent, DateRangePickerComponent],
  template: `
    <div class="flex h-full flex-col gap-4">
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
      </div>

      <!-- Granularity Switcher -->
      <div class="flex gap-1">
        @for (opt of granularityOptions; track opt.value) {
          <button
            (click)="setGranularity(opt.value)"
            class="cursor-pointer rounded-[var(--radius-sm)] px-3 py-1 text-sm transition-colors"
            [class]="granularity() === opt.value
              ? 'bg-[var(--accent-primary)] text-white'
              : 'bg-[var(--bg-secondary)] text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]'"
          >
            {{ opt.labelKey | translate }}
          </button>
        }
      </div>

      <!-- Chart -->
      <div class="flex-1 pb-4">
        <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
          <h3 class="mb-3 text-sm font-medium text-[var(--text-primary)]">
            {{ 'analytics.returns.trend_title' | translate }}
          </h3>
          <dp-chart
            [options]="chartOptions()"
            [loading]="trendQuery.isPending()"
            height="400px"
          />
        </div>
      </div>
    </div>
  `,
})
export class ReturnsTrendPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly navStore = inject(NavigationStore);
  private readonly t = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private readonly initialPeriod =
    this.navStore.getSectionFilterValue<string>('analytics:returns', 'period')
    ?? new Date().toISOString().slice(0, 7);
  private readonly defaultFrom =
    this.navStore.getSectionFilterValue<string>('analytics:returns', 'from')
    ?? monthStart(this.initialPeriod);
  private readonly defaultTo =
    this.navStore.getSectionFilterValue<string>('analytics:returns', 'to')
    ?? monthEnd(this.initialPeriod);

  readonly dateFrom = signal(this.defaultFrom);
  readonly dateTo = signal(this.defaultTo);
  readonly granularity = signal<Granularity>('MONTHLY');
  readonly granularityOptions = GRANULARITY_OPTIONS;

  private readonly filterDefs: UrlFilterDef[] = [
    { key: 'from', signal: this.dateFrom, defaultValue: this.defaultFrom },
    { key: 'to', signal: this.dateTo, defaultValue: this.defaultTo },
    { key: 'granularity', signal: this.granularity as any, defaultValue: 'MONTHLY' },
  ];
  readonly filtersDefault = isFiltersDefault(this.filterDefs);

  constructor() {
    initPersistedFilters(this.router, this.route, {
      pageKey: 'analytics:returns:trend', filterDefs: this.filterDefs,
    });
    const periodHint = inferMonthPeriod(this.dateFrom(), this.dateTo()) ?? this.initialPeriod;
    this.navStore.setSectionFilter('analytics:returns', {
      period: periodHint,
      from: this.dateFrom(),
      to: this.dateTo(),
    });
    this.syncSectionFilter();
  }

  onResetFilters(): void {
    resetFilters(this.filterDefs);
  }

  private readonly filter = computed<AnalyticsFilter>(() => ({
    from: this.dateFrom(),
    to: this.dateTo(),
    granularity: this.granularity(),
  }));

  readonly trendQuery = injectQuery(() => ({
    queryKey: ['analytics', 'returns-trend', this.wsStore.currentWorkspaceId(), this.filter()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getReturnsTrend(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly chartOptions = computed<EChartsOption>(() => {
    const data = this.trendQuery.data() ?? [];
    const periods = data.map((d) => d.period);

    return {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross' },
      },
      legend: {
        data: [this.t.instant('analytics.returns.chart.return_rate'), this.t.instant('analytics.returns.chart.quantity')],
        bottom: 0,
        textStyle: { color: 'var(--text-secondary)', fontSize: 12 },
      },
      grid: { left: 60, right: 60, top: 16, bottom: 40 },
      xAxis: {
        type: 'category',
        data: periods,
        axisLabel: { color: 'var(--text-tertiary)', fontSize: 11 },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: 'var(--border-default)' } },
      },
      yAxis: [
        {
          type: 'value',
          name: this.t.instant('analytics.returns.chart.return_rate'),
          position: 'left',
          axisLabel: {
            formatter: '{value}%',
            color: 'var(--text-tertiary)',
            fontSize: 11,
          },
          splitLine: { lineStyle: { color: 'var(--border-subtle)', type: 'dashed' } },
        },
        {
          type: 'value',
          name: this.t.instant('analytics.returns.chart.quantity'),
          position: 'right',
          axisLabel: { color: 'var(--text-tertiary)', fontSize: 11 },
          splitLine: { show: false },
        },
      ],
      series: [
        {
          name: this.t.instant('analytics.returns.chart.return_rate'),
          type: 'line',
          yAxisIndex: 0,
          data: data.map((d) => d.returnRatePct),
          smooth: true,
          symbol: 'circle',
          symbolSize: 5,
          lineStyle: { color: 'var(--status-error)', width: 2 },
          itemStyle: { color: 'var(--status-error)' },
          markLine: {
            silent: true,
            symbol: 'none',
            lineStyle: { color: 'var(--status-warning)', type: 'dashed', width: 1 },
            data: [{ yAxis: 5, label: { formatter: '5%', position: 'end', fontSize: 10 } }],
          },
        },
        {
          name: this.t.instant('analytics.returns.chart.quantity'),
          type: 'bar',
          yAxisIndex: 1,
          data: data.map((d) => d.returnQuantity),
          barMaxWidth: 24,
          itemStyle: { color: 'rgba(156, 163, 175, 0.3)', borderRadius: [4, 4, 0, 0] },
        },
      ],
    };
  });

  setGranularity(g: Granularity): void {
    this.granularity.set(g);
  }

  private syncSectionFilter(): void {
    effect(() => {
      const from = this.dateFrom();
      const to = this.dateTo();
      this.navStore.setSectionFilter('analytics:returns', {
        period: inferMonthPeriod(from, to) ?? from.slice(0, 7),
        from,
        to,
      });
    });
  }
}
