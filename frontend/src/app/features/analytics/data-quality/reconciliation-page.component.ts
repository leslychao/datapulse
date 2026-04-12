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
import {
  LucideAngularModule,
  CheckCircle,
  AlertTriangle,
  Search,
  BarChart3,
} from 'lucide-angular';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { MarketplaceType, ReconciliationConnection } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { MonthPickerComponent } from '@shared/components/form/month-picker.component';
import { KpiCardComponent, KpiAccent } from '@shared/components/kpi-card.component';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { MarketplaceBadgeComponent } from '@shared/components/marketplace-badge.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { formatMoney, formatPercent, currentMonth } from '@shared/utils/format.utils';
import {
  UrlFilterDef, isFiltersDefault, resetFilters, initPersistedFilters,
} from '@shared/utils/url-filters';

type ReconStatus = ReconciliationConnection['status'];

const STATUS_COLORS: Record<ReconStatus, string> = {
  NORMAL: 'var(--status-success)',
  ANOMALY: 'var(--status-error)',
  INSUFFICIENT_DATA: 'var(--status-warning)',
  CALIBRATION: 'var(--status-info)',
};

const STATUS_COLOR_MAP: Record<ReconStatus, StatusColor> = {
  NORMAL: 'success',
  ANOMALY: 'error',
  INSUFFICIENT_DATA: 'warning',
  CALIBRATION: 'info',
};

const CONNECTION_PALETTE = [
  '#6366f1', '#f43f5e', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899',
];

@Component({
  selector: 'dp-reconciliation-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    LucideAngularModule,
    ChartComponent,
    MonthPickerComponent,
    KpiCardComponent,
    StatusBadgeComponent,
    MarketplaceBadgeComponent,
    EmptyStateComponent,
    SectionCardComponent,
  ],
  template: `
    <div class="flex h-full flex-col gap-4 pb-4">
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

      @if (reconQuery.isPending()) {
        <div class="flex gap-3">
          @for (_ of [1, 2, 3, 4]; track $index) {
            <div class="dp-shimmer h-[72px] flex-1 rounded-[var(--radius-lg)]"></div>
          }
        </div>
        <div class="dp-shimmer h-[280px] w-full rounded-[var(--radius-md)]"></div>
        <div class="dp-shimmer h-[200px] w-full rounded-[var(--radius-md)]"></div>
      } @else if (reconQuery.isError()) {
        <dp-empty-state
          [message]="t.instant('analytics.reconciliation.load_error')"
          [hint]="t.instant('analytics.reconciliation.load_error_hint')"
          [actionLabel]="t.instant('analytics.data_quality.retry')"
          (action)="reconQuery.refetch()"
        />
      } @else if (connections().length === 0) {
        <dp-empty-state
          [message]="t.instant('analytics.reconciliation.empty.no_data')"
          [hint]="t.instant('analytics.reconciliation.empty.no_data_hint')"
        />
      } @else {
        <!-- Summary KPI Strip -->
        <div class="flex flex-wrap gap-3">
          <dp-kpi-card
            [label]="t.instant('analytics.reconciliation.kpi.connections_checked')"
            [value]="connections().length"
            [icon]="icSearch"
            accent="neutral"
          />
          <dp-kpi-card
            [label]="t.instant('analytics.reconciliation.kpi.normal_count')"
            [value]="normalCount()"
            [icon]="icCheckCircle"
            [accent]="normalCount() === connections().length ? 'success' : 'neutral'"
          />
          <dp-kpi-card
            [label]="t.instant('analytics.reconciliation.kpi.anomaly_count')"
            [value]="anomalyCount()"
            [icon]="icAlertTriangle"
            [accent]="anomalyAccent()"
          />
          <dp-kpi-card
            [label]="t.instant('analytics.reconciliation.kpi.max_deviation')"
            [value]="maxDeviationLabel()"
            [icon]="icBarChart"
            [accent]="maxDeviationAccent()"
            [subtitle]="maxDeviationConn()"
          />
        </div>

        <!-- Per-connection cards -->
        <div class="flex flex-col gap-3">
          @for (conn of connections(); track conn.marketplaceType) {
            <div
              class="rounded-[var(--radius-md)] border bg-[var(--bg-primary)] p-4"
              [style.border-color]="statusColor(conn.status)"
            >
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div class="flex items-center gap-3">
                  <dp-marketplace-badge [type]="mpType(conn.marketplaceType)" />
                  <span class="text-sm font-semibold text-[var(--text-primary)]">
                    {{ conn.connectionName }}
                  </span>
                  <dp-status-badge
                    [label]="statusLabel(conn.status)"
                    [color]="statusBadgeColor(conn.status)"
                  />
                </div>
              </div>

              <div class="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-3">
                <div>
                  <div class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                    {{ 'analytics.reconciliation.residual' | translate }}
                  </div>
                  <div class="font-mono text-sm font-bold text-[var(--text-primary)]">
                    {{ fmtMoney(conn.residualAmount) }}
                  </div>
                </div>
                <div>
                  <div class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                    {{ 'analytics.reconciliation.residual_pct' | translate }}
                  </div>
                  <div class="font-mono text-sm font-bold text-[var(--text-primary)]">
                    {{ fmtPct(conn.residualRatioPct) }}
                  </div>
                </div>
                <div>
                  <div class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                    {{ 'analytics.reconciliation.baseline_pct' | translate }}
                  </div>
                  <div class="font-mono text-sm font-bold text-[var(--text-secondary)]">
                    {{ fmtPct(conn.baselineRatioPct) }}
                  </div>
                </div>
              </div>

              <!-- Status explanation -->
              <div class="mt-2 text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                {{ statusHint(conn.status) }}
              </div>

              @if (conn.status === 'ANOMALY') {
                <div
                  class="mt-2 rounded-[var(--radius-sm)] bg-[color-mix(in_srgb,var(--status-error)_6%,transparent)] px-3 py-2 text-[length:var(--text-sm)] text-[var(--status-error)]"
                >
                  {{ anomalyExplanation(conn) }}
                </div>
              }
            </div>
          }
        </div>

        <!-- Residual Trend Chart -->
        <dp-section-card [title]="t.instant('analytics.reconciliation.trend_title')">
          <dp-chart
            [options]="trendChartOptions()"
            [loading]="reconQuery.isPending()"
            height="280px"
          />
        </dp-section-card>

        <!-- Distribution Histogram -->
        <dp-section-card [title]="t.instant('analytics.reconciliation.distribution_title')">
          <dp-chart
            [options]="histogramOptions()"
            [loading]="reconQuery.isPending()"
            height="200px"
          />
        </dp-section-card>
      }
    </div>
  `,
})
export class ReconciliationPageComponent {
  readonly t = inject(TranslateService);
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly icCheckCircle = CheckCircle;
  readonly icAlertTriangle = AlertTriangle;
  readonly icSearch = Search;
  readonly icBarChart = BarChart3;

  readonly period = signal(currentMonth());

  private readonly filterDefs: UrlFilterDef[] = [
    { key: 'period', signal: this.period, defaultValue: currentMonth() },
  ];
  readonly filtersDefault = isFiltersDefault(this.filterDefs);

  constructor() {
    initPersistedFilters(this.router, this.route, {
      pageKey: 'analytics:data-quality:reconciliation', filterDefs: this.filterDefs,
    });
  }

  onResetFilters(): void {
    resetFilters(this.filterDefs);
  }

  readonly reconQuery = injectQuery(() => ({
    queryKey: ['analytics', 'reconciliation', this.wsStore.currentWorkspaceId(), this.period()],
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

  readonly normalCount = computed(() =>
    this.connections().filter((c) => c.status === 'NORMAL').length,
  );

  readonly anomalyCount = computed(() =>
    this.connections().filter((c) => c.status === 'ANOMALY').length,
  );

  readonly anomalyAccent = computed<KpiAccent>(() =>
    this.anomalyCount() > 0 ? 'error' : 'success',
  );

  readonly maxDeviation = computed(() => {
    const conns = this.connections();
    if (conns.length === 0) return null;
    return conns.reduce((max, c) =>
      c.residualRatioPct > (max?.residualRatioPct ?? -1) ? c : max, conns[0]);
  });

  readonly maxDeviationLabel = computed(() => {
    const d = this.maxDeviation();
    return d ? this.fmtPct(d.residualRatioPct) : '—';
  });

  readonly maxDeviationConn = computed(() =>
    this.maxDeviation()?.connectionName ?? '',
  );

  readonly maxDeviationAccent = computed<KpiAccent>(() => {
    const d = this.maxDeviation();
    if (!d) return 'neutral';
    return d.status === 'ANOMALY' ? 'error' : d.status === 'NORMAL' ? 'success' : 'warning';
  });

  readonly trendChartOptions = computed<EChartsOption>(() => {
    const data = this.reconQuery.data();
    if (!data) return {};

    const trend = data.trend;
    const connMap = new Map(data.connections.map((c) => [c.marketplaceType, c.connectionName]));
    const mpTypes = [...new Set(trend.map((t) => t.marketplaceType))];
    const periods = [...new Set(trend.map((t) => t.period))].sort();
    const baselineSuffix = this.t.instant('analytics.reconciliation.baseline_suffix');

    const series: Record<string, unknown>[] = [];
    mpTypes.forEach((mp, idx) => {
      const color = CONNECTION_PALETTE[idx % CONNECTION_PALETTE.length];
      const name = connMap.get(mp) ?? mp;
      const points = trend.filter((t) => t.marketplaceType === mp);

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
        name: `${name} (${baselineSuffix})`,
        type: 'line',
        data: periods.map((p) => points.find((pt) => pt.period === p)?.baselineRatioPct ?? null),
        smooth: true,
        symbol: 'none',
        lineStyle: { color, width: 1, type: 'dashed', opacity: 0.5 },
        itemStyle: { color },
      });
    });

    const legendItems = mpTypes.flatMap((mp) => {
      const name = connMap.get(mp) ?? mp;
      return [name, `${name} (${baselineSuffix})`];
    });

    return {
      tooltip: { trigger: 'axis' },
      legend: { data: legendItems, bottom: 0 },
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

  statusColor(status: ReconStatus): string {
    return STATUS_COLORS[status] ?? 'var(--text-secondary)';
  }

  statusBadgeColor(status: ReconStatus): StatusColor {
    return STATUS_COLOR_MAP[status] ?? 'neutral';
  }

  statusLabel(status: ReconStatus): string {
    return this.t.instant(`analytics.reconciliation.status.${status}`);
  }

  statusHint(status: ReconStatus): string {
    return this.t.instant(`analytics.reconciliation.status_hint.${status}`);
  }

  anomalyExplanation(conn: ReconciliationConnection): string {
    return this.t.instant('analytics.reconciliation.anomaly_explanation', {
      pct: this.fmtPct(conn.residualRatioPct),
      amount: this.fmtMoney(conn.residualAmount),
    });
  }

  mpType(value: string): MarketplaceType {
    return value as MarketplaceType;
  }

  fmtMoney(value: number | null): string {
    return formatMoney(value, 0);
  }

  fmtPct(value: number | null): string {
    return formatPercent(value);
  }
}
