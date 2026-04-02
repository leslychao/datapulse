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
import {
  LucideIconData,
  Banknote,
  Receipt,
  Package,
  Megaphone,
  ChartBar,
  Scale,
} from 'lucide-angular';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { KpiCardComponent, KpiAccent } from '@shared/components/kpi-card.component';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { MonthPickerComponent } from '@shared/components/form/month-picker.component';
import { formatMoney } from '@shared/utils/format.utils';

function currentMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

type TrendDir = 'up' | 'down' | 'neutral';

interface KpiItem {
  labelKey: string;
  formattedValue: string;
  deltaPct: number | null;
  direction: TrendDir;
  icon: LucideIconData;
  accent: KpiAccent;
}

@Component({
  selector: 'dp-pnl-summary-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    ChartComponent,
    KpiCardComponent,
    SectionCardComponent,
    EmptyStateComponent,
    MonthPickerComponent,
  ],
  template: `
    <div class="flex flex-col gap-5">
      <!-- Filter bar -->
      <div class="flex items-center">
        <dp-month-picker [value]="period()" (valueChange)="period.set($event)" />
      </div>

      @if (summaryQuery.isPending()) {
        <div class="flex flex-wrap gap-3">
          @for (_ of shimmerCards; track $index) {
            <dp-kpi-card label="" [loading]="true" />
          }
        </div>

        <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <dp-section-card
            [title]="'analytics.pnl.trend_title' | translate"
            class="lg:col-span-2"
          >
            <div class="dp-shimmer h-[240px] rounded-[var(--radius-sm)]"></div>
          </dp-section-card>
          <dp-section-card [title]="'analytics.pnl.cost_breakdown' | translate">
            <div class="dp-shimmer h-[240px] rounded-[var(--radius-sm)]"></div>
          </dp-section-card>
        </div>
      } @else if (summaryQuery.isError()) {
        <dp-empty-state [message]="'analytics.pnl.load_error' | translate" />
      } @else {
        <div class="flex flex-wrap gap-3">
          @for (kpi of kpiCards(); track kpi.labelKey) {
            <dp-kpi-card
              [label]="kpi.labelKey | translate"
              [value]="kpi.formattedValue"
              [trend]="kpi.deltaPct"
              [trendDirection]="kpi.direction"
              [icon]="kpi.icon"
              [accent]="kpi.accent"
            />
          }
        </div>

        <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <dp-section-card
            [title]="'analytics.pnl.trend_title' | translate"
            class="lg:col-span-2"
          >
            <dp-chart
              [options]="trendOptions()"
              height="240px"
              [loading]="trendQuery.isPending()"
            />
          </dp-section-card>
          <dp-section-card [title]="'analytics.pnl.cost_breakdown' | translate">
            <dp-chart
              [options]="donutOptions()"
              height="240px"
            />
          </dp-section-card>
        </div>
      }
    </div>
  `,
})
export class PnlSummaryPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly t = inject(TranslateService);

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

  readonly kpiCards = computed<KpiItem[]>(() => {
    const s = this.summaryQuery.data();
    if (!s) return [];
    return [
      this.buildKpi('analytics.pnl.kpi.revenue', s.revenueAmount, s.revenueDeltaPct, Banknote, 'success'),
      this.buildKpi('analytics.pnl.kpi.total_costs', s.totalCostsAmount, s.costsDeltaPct, Receipt, 'error'),
      this.buildKpi('analytics.pnl.kpi.cogs', s.cogsAmount, s.cogsDeltaPct, Package, 'warning'),
      this.buildKpi('analytics.pnl.kpi.advertising', s.advertisingCostAmount, s.advertisingDeltaPct, Megaphone, 'info'),
      this.buildKpi('analytics.pnl.kpi.pnl', s.fullPnl, s.pnlDeltaPct, ChartBar, 'primary'),
      this.buildKpi('analytics.pnl.kpi.residual', s.reconciliationResidual, null, Scale, 'neutral'),
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
          name: this.t.instant('analytics.pnl.chart.revenue'),
          type: 'line',
          data: points.map((p) => p.revenueAmount),
          smooth: true,
          itemStyle: { color: '#059669' },
          areaStyle: { color: 'rgba(5,150,105,0.08)' },
        },
        {
          name: this.t.instant('analytics.pnl.chart.costs'),
          type: 'line',
          data: points.map((p) => p.totalCostsAmount),
          smooth: true,
          itemStyle: { color: '#DC2626' },
          areaStyle: { color: 'rgba(220,38,38,0.08)' },
        },
        {
          name: this.t.instant('analytics.pnl.chart.pnl'),
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
          data: items.map((it) => ({
            value: it.amount,
            name: this.t.instant(`analytics.pnl.cost_category.${it.category}`),
          })),
          emphasis: {
            label: { show: true, fontSize: 12, fontWeight: 'bold' },
          },
        },
      ],
    };
  });

  private buildKpi(
    labelKey: string,
    value: number,
    deltaPct: number | null,
    icon: LucideIconData,
    accent: KpiAccent,
  ): KpiItem {
    return {
      labelKey,
      formattedValue: formatMoney(value, 0),
      deltaPct,
      direction: this.trendDir(deltaPct),
      icon,
      accent,
    };
  }

  private trendDir(delta: number | null): TrendDir {
    if (delta == null || delta === 0) return 'neutral';
    return delta > 0 ? 'up' : 'down';
  }
}
