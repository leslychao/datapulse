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
import { ReconciliationConnection } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { formatMoney, formatPercent } from '@shared/utils/format.utils';

function currentMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

type ReconStatus = ReconciliationConnection['status'];

const STATUS_COLORS: Record<ReconStatus, string> = {
  NORMAL: 'var(--status-success)',
  ANOMALY: 'var(--status-error)',
  INSUFFICIENT_DATA: 'var(--status-warning)',
  CALIBRATION: 'var(--status-info)',
};

const STATUS_LABEL_KEYS: Record<ReconStatus, string> = {
  NORMAL: 'analytics.reconciliation.status.NORMAL',
  ANOMALY: 'analytics.reconciliation.status.ANOMALY',
  INSUFFICIENT_DATA: 'analytics.reconciliation.status.INSUFFICIENT_DATA',
  CALIBRATION: 'analytics.reconciliation.status.CALIBRATION',
};

const CONNECTION_PALETTE = [
  '#6366f1', '#f43f5e', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899',
];

@Component({
  selector: 'dp-reconciliation-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, ChartComponent],
  template: `
    <div class="flex h-full flex-col gap-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3">
        <input
          type="month"
          [value]="period()"
          (change)="onPeriodChange($event)"
          class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]
                 px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-primary)]
                 outline-none focus:border-[var(--accent-primary)]"
        />
      </div>

      @if (reconQuery.isPending()) {
        <div class="space-y-4 pb-4">
          <div class="dp-shimmer h-24 w-full rounded-[var(--radius-md)]"></div>
          <div class="dp-shimmer h-[280px] w-full rounded-[var(--radius-md)]"></div>
          <div class="dp-shimmer h-[200px] w-full rounded-[var(--radius-md)]"></div>
        </div>
      } @else if (reconQuery.isError()) {
        <div class="text-sm text-[var(--status-error)]">
          {{ 'analytics.reconciliation.load_error' | translate }}
        </div>
      } @else {
        <div class="space-y-4 pb-4">
          <!-- Per-connection KPI Cards -->
          <div class="flex flex-wrap gap-3">
            @for (conn of connections(); track conn.connectionId) {
              <div
                class="flex min-w-[240px] flex-col gap-2 rounded-[var(--radius-md)] border bg-[var(--bg-primary)] p-4"
                [style.border-color]="statusColor(conn.status)"
              >
                <div class="flex items-center justify-between">
                  <span class="text-sm font-medium text-[var(--text-primary)]">
                    {{ conn.connectionName }}
                  </span>
                  <span
                    class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium"
                    [style.background]="'color-mix(in srgb, ' + statusColor(conn.status) + ' 12%, transparent)'"
                    [style.color]="statusColor(conn.status)"
                  >
                    <span
                      class="inline-block h-1.5 w-1.5 rounded-full"
                      [style.background]="statusColor(conn.status)"
                    ></span>
                    {{ statusLabel(conn.status) }}
                  </span>
                </div>

                <div class="grid grid-cols-3 gap-2">
                  <div>
                    <div class="text-[11px] text-[var(--text-tertiary)]">{{ 'analytics.reconciliation.residual' | translate }}</div>
                    <div class="font-mono text-sm font-bold text-[var(--text-primary)]">
                      {{ formatMoney(conn.residualAmount) }}
                    </div>
                  </div>
                  <div>
                    <div class="text-[11px] text-[var(--text-tertiary)]">{{ 'analytics.reconciliation.residual_pct' | translate }}</div>
                    <div class="font-mono text-sm font-bold text-[var(--text-primary)]">
                      {{ formatPct(conn.residualRatioPct) }}
                    </div>
                  </div>
                  <div>
                    <div class="text-[11px] text-[var(--text-tertiary)]">{{ 'analytics.reconciliation.baseline_pct' | translate }}</div>
                    <div class="font-mono text-sm font-bold text-[var(--text-secondary)]">
                      {{ formatPct(conn.baselineRatioPct) }}
                    </div>
                  </div>
                </div>
              </div>
            }
          </div>

          <!-- Residual Trend Chart -->
          <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
            <h3 class="mb-3 text-sm font-medium text-[var(--text-primary)]">
              {{ 'analytics.reconciliation.trend_title' | translate }}
            </h3>
            <dp-chart
              [options]="trendChartOptions()"
              [loading]="reconQuery.isPending()"
              height="280px"
            />
          </div>

          <!-- Distribution Histogram -->
          <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
            <h3 class="mb-3 text-sm font-medium text-[var(--text-primary)]">
              {{ 'analytics.reconciliation.distribution_title' | translate }}
            </h3>
            <dp-chart
              [options]="histogramOptions()"
              [loading]="reconQuery.isPending()"
              height="200px"
            />
          </div>
        </div>
      }
    </div>
  `,
})
export class ReconciliationPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly t = inject(TranslateService);

  readonly period = signal(currentMonth());

  readonly reconQuery = injectQuery(() => ({
    queryKey: ['reconciliation', this.wsStore.currentWorkspaceId(), this.period()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getReconciliation(
          this.wsStore.currentWorkspaceId()!,
          { period: this.period() },
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly connections = computed<ReconciliationConnection[]>(() =>
    this.reconQuery.data()?.connections ?? [],
  );

  readonly trendChartOptions = computed<EChartsOption>(() => {
    const data = this.reconQuery.data();
    if (!data) return {};

    const trend = data.trend;
    const connMap = new Map(data.connections.map((c) => [c.connectionId, c.connectionName]));
    const connIds = [...new Set(trend.map((t) => t.connectionId))];
    const periods = [...new Set(trend.map((t) => t.period))].sort();

    const series: Record<string, unknown>[] = [];
    connIds.forEach((id, idx) => {
      const color = CONNECTION_PALETTE[idx % CONNECTION_PALETTE.length];
      const name = connMap.get(id) ?? `#${id}`;
      const points = trend.filter((t) => t.connectionId === id);

      series.push({
        name,
        type: 'line',
        data: periods.map((p) => points.find((pt) => pt.period === p)?.residualRatioPct ?? null),
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        lineStyle: { color, width: 2 },
        itemStyle: { color },
      });

      series.push({
        name: `${name} baseline`,
        type: 'line',
        data: periods.map((p) => points.find((pt) => pt.period === p)?.baselineRatioPct ?? null),
        smooth: true,
        symbol: 'none',
        lineStyle: { color, width: 1, type: 'dashed', opacity: 0.5 },
        itemStyle: { color },
      });
    });

    return {
      tooltip: { trigger: 'axis' },
      legend: {
        data: connIds.map((id) => connMap.get(id) ?? `#${id}`),
        bottom: 0,
        textStyle: { color: 'var(--text-secondary)', fontSize: 11 },
      },
      grid: { left: 50, right: 20, top: 12, bottom: 40 },
      xAxis: {
        type: 'category',
        data: periods,
        axisLabel: { color: 'var(--text-tertiary)', fontSize: 11 },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: 'var(--border-default)' } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { formatter: '{value}%', color: 'var(--text-tertiary)', fontSize: 11 },
        splitLine: { lineStyle: { color: 'var(--border-subtle)', type: 'dashed' } },
      },
      series,
    };
  });

  readonly histogramOptions = computed<EChartsOption>(() => {
    const buckets = this.reconQuery.data()?.distribution ?? [];

    return {
      tooltip: {
        trigger: 'axis',
        formatter: (params: unknown) => {
          const arr = Array.isArray(params) ? params : [params];
          const p = arr[0] as { name: string; value: number };
          return this.t.instant('analytics.reconciliation.histogram_tooltip', {
            label: p.name,
            count: p.value,
          });
        },
      },
      grid: { left: 50, right: 20, top: 8, bottom: 24 },
      xAxis: {
        type: 'category',
        data: buckets.map((b) => b.label),
        axisLabel: { color: 'var(--text-tertiary)', fontSize: 11, rotate: 30 },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: 'var(--border-default)' } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: 'var(--text-tertiary)', fontSize: 11 },
        splitLine: { lineStyle: { color: 'var(--border-subtle)', type: 'dashed' } },
      },
      series: [
        {
          type: 'bar',
          data: buckets.map((b) => ({
            value: b.count,
            itemStyle: {
              color: b.to > 100 ? 'var(--status-error)' : 'var(--accent-primary)',
              borderRadius: [4, 4, 0, 0],
            },
          })),
          barMaxWidth: 32,
        },
      ],
    };
  });

  onPeriodChange(event: Event): void {
    this.period.set((event.target as HTMLInputElement).value);
  }

  statusColor(status: ReconStatus): string {
    return STATUS_COLORS[status] ?? 'var(--text-secondary)';
  }

  statusLabel(status: ReconStatus): string {
    const key = STATUS_LABEL_KEYS[status];
    return key ? this.t.instant(key) : status;
  }

  formatMoney(value: number | null): string {
    return formatMoney(value, 0);
  }

  formatPct(value: number | null): string {
    return formatPercent(value);
  }
}
