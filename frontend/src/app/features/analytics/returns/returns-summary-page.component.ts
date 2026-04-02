import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import type { EChartsOption } from 'echarts';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { formatMoney, formatPercent } from '@shared/utils/format.utils';

function currentMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

@Component({
  selector: 'dp-returns-summary-page',
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

      @if (summaryQuery.isPending()) {
        <div class="flex gap-3">
          @for (_ of [1, 2, 3]; track $index) {
            <div class="dp-shimmer h-[72px] flex-1 rounded-[var(--radius-md)]"></div>
          }
        </div>
      }

      @if (summaryQuery.data(); as s) {
        <!-- KPI Cards -->
        <div class="flex gap-3">
          <div class="flex flex-1 flex-col gap-1 rounded-[var(--radius-md)] bg-[var(--bg-primary)] p-3 shadow-[var(--shadow-sm)]">
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
          <div class="flex flex-1 flex-col gap-1 rounded-[var(--radius-md)] bg-[var(--bg-primary)] p-3 shadow-[var(--shadow-sm)]">
            <span class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
              {{ 'analytics.returns.kpi.total_refund' | translate }}
            </span>
            <span class="font-mono text-[length:var(--text-lg)] font-semibold text-[var(--status-error)]">
              {{ formatMoney(s.totalRefundAmount) }}
            </span>
          </div>
          <div class="flex flex-1 flex-col gap-1 rounded-[var(--radius-md)] bg-[var(--bg-primary)] p-3 shadow-[var(--shadow-sm)]">
            <span class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
              {{ 'analytics.returns.kpi.top_reason' | translate }}
            </span>
            <span class="text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]">
              {{ s.topReturnReason || '—' }}
            </span>
          </div>
        </div>
      }

      <!-- Chart + Penalties side-by-side -->
      <div class="grid grid-cols-2 gap-4 pb-4">
        <!-- Return Reasons Chart -->
        <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
          <h3 class="mb-3 text-sm font-medium text-[var(--text-primary)]">
            {{ 'analytics.returns.reasons_chart_title' | translate }}
          </h3>
          <dp-chart
            [options]="reasonChartOptions()"
            [loading]="summaryQuery.isPending()"
            height="200px"
          />
        </div>

        <!-- Penalties Breakdown -->
        <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
          <h3 class="mb-3 text-sm font-medium text-[var(--text-primary)]">
            {{ 'analytics.returns.penalties_title' | translate }}
          </h3>

          @if (summaryQuery.isPending()) {
            <div class="space-y-2">
              @for (_ of [1, 2, 3]; track $index) {
                <div class="dp-shimmer h-5 w-full rounded-[var(--radius-sm)]"></div>
              }
            </div>
          } @else {
            <ul class="space-y-2">
              @for (item of penaltyBreakdown(); track item.type) {
                <li class="flex items-center justify-between text-sm">
                  <span class="text-[var(--text-secondary)]">{{ item.type }}</span>
                  <span class="font-mono text-[var(--status-error)]">
                    {{ formatMoney(item.amount) }}
                  </span>
                </li>
              }
            </ul>

            <div class="mt-3 flex items-center justify-between border-t border-[var(--border-subtle)] pt-3 text-sm font-medium">
              <span class="text-[var(--text-primary)]">
                {{ 'analytics.returns.penalties_total' | translate }}
              </span>
              <span class="font-mono text-[var(--status-error)]">
                {{ formatMoney(totalPenalties()) }}
              </span>
            </div>
          }
        </div>
      </div>
    </div>
  `,
})
export class ReturnsSummaryPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);

  readonly period = signal(currentMonth());

  readonly summaryQuery = injectQuery(() => ({
    queryKey: ['returns-summary', this.wsStore.currentWorkspaceId(), this.period()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getReturnsSummary(
          this.wsStore.currentWorkspaceId()!,
          { period: this.period() },
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly penaltyBreakdown = computed(() =>
    this.summaryQuery.data()?.penaltyBreakdown ?? [],
  );

  readonly totalPenalties = computed(() =>
    this.summaryQuery.data()?.totalPenalties ?? 0,
  );

  readonly reasonChartOptions = computed<EChartsOption>(() => {
    const items = this.summaryQuery.data()?.reasonBreakdown ?? [];
    return {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 120, right: 40, top: 8, bottom: 8 },
      xAxis: { type: 'value', show: false },
      yAxis: {
        type: 'category',
        data: items.map((i) => i.reason),
        inverse: true,
        axisLabel: {
          width: 110,
          overflow: 'truncate',
          color: 'var(--text-secondary)',
          fontSize: 12,
        },
        axisTick: { show: false },
        axisLine: { show: false },
      },
      series: [
        {
          type: 'bar',
          data: items.map((i) => i.percent),
          barMaxWidth: 18,
          itemStyle: {
            color: 'var(--accent-primary)',
            borderRadius: [0, 4, 4, 0],
          },
          label: {
            show: true,
            position: 'right',
            formatter: '{c}%',
            fontSize: 11,
            color: 'var(--text-secondary)',
          },
        },
      ],
    };
  });

  onPeriodChange(event: Event): void {
    this.period.set((event.target as HTMLInputElement).value);
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
