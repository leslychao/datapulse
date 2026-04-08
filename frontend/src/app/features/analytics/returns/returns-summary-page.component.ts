import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import type { EChartsOption } from 'echarts';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { NavigationStore } from '@shared/stores/navigation.store';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { MonthPickerComponent } from '@shared/components/form/month-picker.component';
import { formatMoney, formatPercent, currentMonth } from '@shared/utils/format.utils';
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

@Component({
  selector: 'dp-returns-summary-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, ChartComponent, MonthPickerComponent],
  template: `
    <div class="flex h-full flex-col gap-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3">
        <dp-month-picker [value]="period()" (valueChange)="period.set($event)" />
        @if (!filtersDefault()) {
          <button type="button" (click)="onResetFilters()"
            class="h-8 cursor-pointer rounded-[var(--radius-md)] px-3 text-[length:var(--text-sm)]
                   text-[var(--text-tertiary)] transition-colors
                   hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]">
            {{ 'filter_bar.reset_all' | translate }}
          </button>
        }
      </div>
      <p class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
        {{ 'analytics.returns.note.operational_scope' | translate }}
      </p>

      @if (summaryQuery.isPending()) {
        <div class="flex gap-3">
          @for (_ of [1, 2, 3]; track $index) {
            <div class="dp-shimmer h-[72px] flex-1 rounded-[var(--radius-md)]"></div>
          }
        </div>
      }

      @if (summaryQuery.data(); as s) {
        @if (s.totalReturnCount === 0) {
          <div
            class="rounded-[var(--radius-md)] border border-[var(--border-default)]
                   bg-[var(--bg-secondary)] px-3 py-2 text-[length:var(--text-xs)]
                   text-[var(--text-secondary)]"
          >
            {{ 'analytics.returns.empty_operational_month' | translate }}
          </div>
        }

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
              {{ 'analytics.returns.kpi.return_count' | translate }}
            </span>
            <span class="font-mono text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]">
              {{ s.totalReturnCount }}
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

      <!-- Chart + Return Amount side-by-side -->
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

        <!-- Return Amount -->
        <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
          <h3 class="mb-3 text-sm font-medium text-[var(--text-primary)]">
            {{ 'analytics.returns.kpi.return_amount' | translate }}
          </h3>
          @if (summaryQuery.isPending()) {
            <div class="dp-shimmer h-10 w-40 rounded-[var(--radius-sm)]"></div>
          } @else {
            @if (summaryQuery.data(); as s) {
              <span class="font-mono text-2xl font-bold text-[var(--text-primary)]">
                {{ formatMoney(s.totalReturnAmount) }}
              </span>
              <p class="mt-2 text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                {{ 'analytics.returns.kpi.return_amount_hint' | translate }}
              </p>
            }
          }
        </div>
      </div>
    </div>
  `,
})
export class ReturnsSummaryPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly navStore = inject(NavigationStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly period = signal(
    this.navStore.getSectionFilterValue<string>('analytics:returns', 'period') ?? currentMonth()
  );

  private readonly filterDefs: UrlFilterDef[] = [
    { key: 'period', signal: this.period, defaultValue: currentMonth() },
  ];
  readonly filtersDefault = isFiltersDefault(this.filterDefs);

  constructor() {
    initPersistedFilters(this.router, this.route, {
      pageKey: 'analytics:returns:summary', filterDefs: this.filterDefs,
    });
    effect(() => {
      const period = this.period();
      this.navStore.setSectionFilter('analytics:returns', {
        period,
        from: monthStart(period),
        to: monthEnd(period),
      });
    });
  }

  onResetFilters(): void {
    resetFilters(this.filterDefs);
  }

  readonly summaryQuery = injectQuery(() => ({
    queryKey: ['analytics', 'returns-summary', this.wsStore.currentWorkspaceId(), this.period()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getReturnsSummary(
          this.wsStore.currentWorkspaceId()!,
          { period: this.period() },
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

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
