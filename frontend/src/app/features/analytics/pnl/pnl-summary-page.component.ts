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

function currentMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

interface KpiCard {
  labelKey: string;
  value: number;
  deltaPct: number | null;
}

@Component({
  selector: 'dp-pnl-summary-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, ChartComponent],
  template: `
    <div class="flex flex-col gap-4">
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
        <div class="grid grid-cols-3 gap-3 lg:grid-cols-6">
          @for (i of shimmerCards; track i) {
            <div class="dp-shimmer h-[88px] rounded-[var(--radius-md)]"></div>
          }
        </div>
      }

      @if (summaryQuery.data(); as s) {
        <!-- KPI Cards -->
        <div class="grid grid-cols-3 gap-3 lg:grid-cols-6">
          @for (kpi of kpiCards(); track kpi.labelKey) {
            <div
              class="flex flex-col gap-1 rounded-[var(--radius-md)] bg-[var(--bg-primary)]
                     p-3 shadow-[var(--shadow-sm)]"
            >
              <span class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                {{ kpi.labelKey | translate }}
              </span>
              <span class="font-mono text-[length:var(--text-lg)] font-semibold"
                    [class]="moneyColorClass(kpi.value)">
                {{ formatMoney(kpi.value) }}
              </span>
              @if (kpi.deltaPct != null) {
                <span class="text-[length:var(--text-xs)] font-mono"
                      [class]="formatDelta(kpi.deltaPct).colorClass">
                  {{ formatDelta(kpi.deltaPct).text }}
                </span>
              }
            </div>
          }
        </div>

        <!-- Charts row -->
        <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <div class="col-span-1 rounded-[var(--radius-md)] bg-[var(--bg-primary)] p-4
                      shadow-[var(--shadow-sm)] lg:col-span-2">
            <h3 class="mb-2 text-[length:var(--text-sm)] font-medium text-[var(--text-primary)]">
              {{ 'analytics.pnl.trend_title' | translate }}
            </h3>
            <dp-chart
              [options]="trendOptions()"
              height="200px"
              [loading]="trendQuery.isPending()"
            />
          </div>
          <div class="rounded-[var(--radius-md)] bg-[var(--bg-primary)] p-4 shadow-[var(--shadow-sm)]">
            <h3 class="mb-2 text-[length:var(--text-sm)] font-medium text-[var(--text-primary)]">
              {{ 'analytics.pnl.cost_breakdown' | translate }}
            </h3>
            <dp-chart
              [options]="donutOptions()"
              height="240px"
              [loading]="summaryQuery.isPending()"
            />
          </div>
        </div>
      }
    </div>
  `,
})
export class PnlSummaryPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);

  readonly period = signal(currentMonth());
  readonly shimmerCards = Array.from({ length: 6 });

  readonly summaryQuery = injectQuery(() => ({
    queryKey: ['pnl-summary', this.wsStore.currentWorkspaceId(), this.period()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getPnlSummary(this.wsStore.currentWorkspaceId()!, {
          period: this.period(),
        }),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly trendQuery = injectQuery(() => ({
    queryKey: ['pnl-trend', this.wsStore.currentWorkspaceId(), this.period()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getPnlTrend(this.wsStore.currentWorkspaceId()!, {
          period: this.period(),
        }),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly kpiCards = computed<KpiCard[]>(() => {
    const s = this.summaryQuery.data();
    if (!s) return [];
    return [
      { labelKey: 'analytics.pnl.kpi.revenue', value: s.revenueAmount, deltaPct: s.revenueDeltaPct },
      { labelKey: 'analytics.pnl.kpi.total_costs', value: s.totalCostsAmount, deltaPct: s.costsDeltaPct },
      { labelKey: 'analytics.pnl.kpi.cogs', value: s.cogsAmount, deltaPct: s.cogsDeltaPct },
      { labelKey: 'analytics.pnl.kpi.advertising', value: s.advertisingCostAmount, deltaPct: s.advertisingDeltaPct },
      { labelKey: 'analytics.pnl.kpi.pnl', value: s.fullPnl, deltaPct: s.pnlDeltaPct },
      { labelKey: 'analytics.pnl.kpi.residual', value: s.reconciliationResidual, deltaPct: null },
    ];
  });

  readonly trendOptions = computed<EChartsOption>(() => {
    const points = this.trendQuery.data() ?? [];
    const periods = points.map((p) => p.period);
    return {
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0, textStyle: { fontSize: 11 } },
      grid: { top: 10, right: 16, bottom: 36, left: 60 },
      xAxis: { type: 'category', data: periods, axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', axisLabel: { fontSize: 11 } },
      series: [
        {
          name: 'Выручка',
          type: 'line',
          data: points.map((p) => p.revenueAmount),
          smooth: true,
          itemStyle: { color: '#059669' },
          areaStyle: { color: 'rgba(5,150,105,0.08)' },
        },
        {
          name: 'Затраты',
          type: 'line',
          data: points.map((p) => p.totalCostsAmount),
          smooth: true,
          itemStyle: { color: '#DC2626' },
          areaStyle: { color: 'rgba(220,38,38,0.08)' },
        },
        {
          name: 'P&L',
          type: 'line',
          data: points.map((p) => p.fullPnl),
          smooth: true,
          itemStyle: { color: '#2563EB' },
          areaStyle: { color: 'rgba(37,99,235,0.08)' },
        },
      ],
    };
  });

  readonly donutOptions = computed<EChartsOption>(() => {
    const s = this.summaryQuery.data();
    const items = s?.costBreakdown ?? [];
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ₽ ({d}%)' },
      series: [
        {
          type: 'pie',
          radius: ['55%', '75%'],
          center: ['50%', '50%'],
          label: { show: false },
          data: items.map((it) => ({ value: it.amount, name: it.category })),
          emphasis: {
            label: { show: true, fontSize: 12, fontWeight: 'bold' },
          },
        },
      ],
    };
  });

  onPeriodChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.period.set(input.value);
  }

  formatMoney(value: number | null): string {
    if (value == null) return '—';
    const abs = Math.abs(value);
    const formatted = abs.toLocaleString('ru-RU', { maximumFractionDigits: 0 });
    const sign = value < 0 ? '−' : '';
    return `${sign}${formatted} ₽`;
  }

  formatPct(value: number | null): string {
    if (value == null) return '—';
    return (
      value.toLocaleString('ru-RU', {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
      }) + '%'
    );
  }

  formatDelta(value: number | null): { text: string; colorClass: string } {
    if (value == null) return { text: '', colorClass: '' };
    if (value > 0)
      return {
        text: `↑ ${this.formatPct(value)}`,
        colorClass: 'text-[var(--finance-positive)]',
      };
    if (value < 0)
      return {
        text: `↓ ${this.formatPct(Math.abs(value))}`,
        colorClass: 'text-[var(--finance-negative)]',
      };
    return {
      text: `→ ${this.formatPct(0)}`,
      colorClass: 'text-[var(--finance-zero)]',
    };
  }

  moneyColorClass(value: number): string {
    if (value > 0) return 'text-[var(--finance-positive)]';
    if (value < 0) return 'text-[var(--finance-negative)]';
    return 'text-[var(--finance-zero)]';
  }
}
