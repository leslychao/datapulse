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
import {
  LucideIconData,
  Banknote,
  Receipt,
  Package,
  Megaphone,
  ChartBar,
  Scale,
  ShieldCheck,
  RotateCcw,
} from 'lucide-angular';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { NavigationStore } from '@shared/stores/navigation.store';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { GuidedTourService } from '@shared/services/guided-tour.service';
import { TourProgressStore } from '@shared/stores/tour-progress.store';
import { PNL_OVERVIEW_TOUR } from '../tours/pnl-tours';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { KpiCardComponent, KpiAccent } from '@shared/components/kpi-card.component';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { MonthPickerComponent } from '@shared/components/form/month-picker.component';
import { formatMoney, currentMonth } from '@shared/utils/format.utils';
import {
  UrlFilterDef, isFiltersDefault, resetFilters, initPersistedFilters,
} from '@shared/utils/url-filters';

function resolveCssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || name;
}

type TrendDir = 'up' | 'down' | 'neutral';

interface KpiItem {
  labelKey: string;
  formattedValue: string;
  deltaPct: number | null;
  direction: TrendDir;
  icon: LucideIconData;
  accent: KpiAccent;
  route: string;
  invertTrend?: boolean;
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
      <div class="flex items-center gap-3" data-tour="pnl-period-filter">
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
            <div class="dp-shimmer h-[200px] rounded-[var(--radius-sm)]"></div>
          </dp-section-card>
          <dp-section-card [title]="'analytics.pnl.cost_breakdown' | translate">
            <div class="dp-shimmer h-[200px] rounded-[var(--radius-sm)]"></div>
          </dp-section-card>
        </div>
      } @else if (summaryQuery.isError()) {
        <dp-empty-state [message]="'analytics.pnl.load_error' | translate" />
      } @else {
        <div class="flex flex-wrap gap-3" data-tour="pnl-kpi-cards">
          @for (kpi of kpiCards(); track kpi.labelKey) {
            <div class="cursor-pointer transition-opacity hover:opacity-80" (click)="navigateToKpi(kpi)">
              <dp-kpi-card
                [label]="kpi.labelKey | translate"
                [value]="kpi.formattedValue"
                [trend]="kpi.deltaPct"
                [trendDirection]="kpi.direction"
                [icon]="kpi.icon"
                [accent]="kpi.accent"
              />
            </div>
          }
        </div>

        <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <dp-section-card
            data-tour="pnl-trend-chart"
            [title]="'analytics.pnl.trend_title' | translate"
            class="lg:col-span-2"
          >
            <dp-chart
              [options]="trendOptions()"
              height="200px"
              [loading]="trendQuery.isPending()"
            />
          </dp-section-card>
          <dp-section-card data-tour="pnl-cost-breakdown" [title]="'analytics.pnl.cost_breakdown' | translate">
            <dp-chart
              [options]="donutOptions()"
              height="200px"
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
  private readonly navStore = inject(NavigationStore);
  private readonly tourService = inject(GuidedTourService);
  private readonly tourProgress = inject(TourProgressStore);

  constructor() {
    effect(() => {
      this.navStore.setSectionFilter('analytics:pnl', { period: this.period() });
    });
    initPersistedFilters(this.router, this.route, {
      pageKey: 'analytics:pnl:summary', filterDefs: this.filterDefs,
    });
    if (PNL_OVERVIEW_TOUR.triggerOnFirstVisit && !this.tourProgress.isCompleted(PNL_OVERVIEW_TOUR.id)) {
      this.tourService.startWhenReady(PNL_OVERVIEW_TOUR);
    }
  }
  private readonly t = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly period = signal(this.navStore.getSectionFilterValue<string>('analytics:pnl', 'period') ?? currentMonth());
  readonly shimmerCards = Array.from({ length: 8 });

  private readonly filterDefs: UrlFilterDef[] = [
    { key: 'period', signal: this.period, defaultValue: currentMonth() },
  ];
  readonly filtersDefault = isFiltersDefault(this.filterDefs);

  onResetFilters(): void {
    resetFilters(this.filterDefs);
  }

  readonly summaryQuery = injectQuery(() => ({
    queryKey: ['analytics', 'pnl-summary', this.wsStore.currentWorkspaceId(), this.period()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getPnlSummary(this.wsStore.currentWorkspaceId()!, {
          period: this.period(),
        }),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly trendQuery = injectQuery(() => ({
    queryKey: ['analytics', 'pnl-trend', this.wsStore.currentWorkspaceId(), this.period()],
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
      this.buildKpi('analytics.pnl.kpi.revenue', s.revenueAmount, s.revenueDeltaPct, Banknote, 'success', 'pnl/by-product'),
      this.buildKpi('analytics.pnl.kpi.total_costs', s.totalCostsAmount, s.costsDeltaPct, Receipt, 'error', 'pnl/by-product', true),
      this.buildKpi('analytics.pnl.kpi.compensation', s.compensationAmount, s.compensationDeltaPct, ShieldCheck, 'info', 'pnl/by-posting'),
      this.buildKpi('analytics.pnl.kpi.refund', s.refundAmount, s.refundDeltaPct, RotateCcw, 'warning', 'pnl/by-posting', true),
      this.buildKpi('analytics.pnl.kpi.cogs', s.cogsAmount, s.cogsDeltaPct, Package, 'warning', 'pnl/by-product', true),
      this.buildKpi('analytics.pnl.kpi.advertising', s.advertisingCostAmount, s.advertisingDeltaPct, Megaphone, 'info', 'pnl/by-product', true),
      this.buildKpi('analytics.pnl.kpi.pnl', s.fullPnl, s.pnlDeltaPct, ChartBar, 'primary', 'pnl/trend'),
      this.buildKpi('analytics.pnl.kpi.residual', s.reconciliationResidual, null, Scale, 'neutral', 'data-quality/reconciliation'),
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
          itemStyle: { color: resolveCssVar('--finance-positive') },
          areaStyle: { color: resolveCssVar('--finance-positive') + '14' },
        },
        {
          name: this.t.instant('analytics.pnl.chart.costs'),
          type: 'line',
          data: points.map((p) => p.totalCostsAmount),
          smooth: true,
          itemStyle: { color: resolveCssVar('--finance-negative') },
          areaStyle: { color: resolveCssVar('--finance-negative') + '14' },
        },
        {
          name: this.t.instant('analytics.pnl.chart.pnl'),
          type: 'line',
          data: points.map((p) => p.fullPnl),
          smooth: true,
          itemStyle: { color: resolveCssVar('--accent-primary') },
          areaStyle: { color: resolveCssVar('--accent-primary') + '14' },
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
            value: Math.abs(it.amount),
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
    route: string,
    invertTrend = false,
  ): KpiItem {
    const rawDir = this.trendDir(deltaPct);
    const direction: TrendDir = invertTrend
      ? rawDir === 'up' ? 'down' : rawDir === 'down' ? 'up' : 'neutral'
      : rawDir;
    return {
      labelKey,
      formattedValue: formatMoney(value, 0),
      deltaPct,
      direction,
      icon,
      accent,
      route,
      invertTrend,
    };
  }

  navigateToKpi(kpi: KpiItem): void {
    this.router.navigate([kpi.route], { relativeTo: this.route.parent });
  }

  private trendDir(delta: number | null): TrendDir {
    if (delta == null || delta === 0) return 'neutral';
    return delta > 0 ? 'up' : 'down';
  }
}
