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
import { AnalyticsFilter, Granularity } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';

function defaultDateFrom(): string {
  const d = new Date();
  d.setMonth(d.getMonth() - 3);
  return d.toISOString().slice(0, 10);
}

function defaultDateTo(): string {
  return new Date().toISOString().slice(0, 10);
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
  imports: [TranslatePipe, ChartComponent],
  template: `
    <div class="flex h-full flex-col gap-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3 px-4">
        <input
          type="date"
          [value]="dateFrom()"
          (change)="onDateFromChange($event)"
          class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]
                 px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-primary)]
                 outline-none focus:border-[var(--accent-primary)]"
        />
        <span class="text-[var(--text-tertiary)]">—</span>
        <input
          type="date"
          [value]="dateTo()"
          (change)="onDateToChange($event)"
          class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]
                 px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-primary)]
                 outline-none focus:border-[var(--accent-primary)]"
        />
      </div>

      <!-- Granularity Switcher -->
      <div class="flex gap-1 px-4">
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
      <div class="flex-1 px-4 pb-4">
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
  private readonly t = inject(TranslateService);

  readonly dateFrom = signal(defaultDateFrom());
  readonly dateTo = signal(defaultDateTo());
  readonly granularity = signal<Granularity>('DAILY');
  readonly granularityOptions = GRANULARITY_OPTIONS;

  private readonly filter = computed<AnalyticsFilter>(() => ({
    from: this.dateFrom(),
    to: this.dateTo(),
    granularity: this.granularity(),
  }));

  readonly trendQuery = injectQuery(() => ({
    queryKey: ['returns-trend', this.wsStore.currentWorkspaceId(), this.filter()],
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

  onDateFromChange(event: Event): void {
    this.dateFrom.set((event.target as HTMLInputElement).value);
  }

  onDateToChange(event: Event): void {
    this.dateTo.set((event.target as HTMLInputElement).value);
  }

  setGranularity(g: Granularity): void {
    this.granularity.set(g);
  }
}
