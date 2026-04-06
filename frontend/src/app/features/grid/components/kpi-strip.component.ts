import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';
import { Package, Percent, Clock, AlertTriangle, TrendingUp } from 'lucide-angular';

import { OfferApiService } from '@core/api/offer-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { formatMoney, formatPercent } from '@shared/utils/format.utils';

@Component({
  selector: 'dp-kpi-strip',
  standalone: true,
  imports: [KpiCardComponent, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { style: 'display:contents' },
  template: `
    <div class="flex flex-wrap gap-3 px-4 pt-3" data-tour="grid-kpi-strip">
      <dp-kpi-card
        [label]="'grid.kpi.total_offers' | translate"
        [value]="totalOffers()"
        [icon]="PackageIcon"
        accent="primary"
        [loading]="kpiQuery.isPending()"
      />
      <dp-kpi-card
        [label]="'grid.kpi.avg_margin' | translate"
        [value]="avgMarginDisplay()"
        [icon]="PercentIcon"
        accent="info"
        [trend]="kpiQuery.data()?.avgMarginTrend ?? null"
        [trendDirection]="avgMarginTrendDir()"
        [loading]="kpiQuery.isPending()"
      />
      <dp-kpi-card
        [label]="'grid.kpi.pending_actions' | translate"
        [value]="kpiQuery.data()?.pendingActionsCount ?? null"
        [icon]="ClockIcon"
        accent="warning"
        [loading]="kpiQuery.isPending()"
      />
      <dp-kpi-card
        [label]="'grid.kpi.critical_stock' | translate"
        [value]="kpiQuery.data()?.criticalStockCount ?? null"
        [icon]="AlertTriangleIcon"
        accent="error"
        [loading]="kpiQuery.isPending()"
      />
      <dp-kpi-card
        [label]="'grid.kpi.revenue_30d' | translate"
        [value]="revenueDisplay()"
        [icon]="TrendingUpIcon"
        accent="success"
        [trend]="kpiQuery.data()?.revenue30dTrend ?? null"
        [trendDirection]="revenueTrendDir()"
        [loading]="kpiQuery.isPending()"
      />
    </div>
  `,
})
export class KpiStripComponent {
  private readonly offerApi = inject(OfferApiService);
  private readonly wsStore = inject(WorkspaceContextStore);

  protected readonly PackageIcon = Package;
  protected readonly PercentIcon = Percent;
  protected readonly ClockIcon = Clock;
  protected readonly AlertTriangleIcon = AlertTriangle;
  protected readonly TrendingUpIcon = TrendingUp;

  readonly kpiQuery = injectQuery(() => ({
    queryKey: ['grid-kpi', this.wsStore.currentWorkspaceId()],
    queryFn: () => lastValueFrom(this.offerApi.getGridKpi(this.wsStore.currentWorkspaceId()!)),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  protected readonly totalOffers = computed(() => {
    const v = this.kpiQuery.data()?.totalOffers;
    return v !== undefined && v !== null ? v.toLocaleString('ru-RU') : null;
  });

  protected readonly avgMarginDisplay = computed(() => {
    const v = this.kpiQuery.data()?.avgMarginPct;
    if (v === null || v === undefined) return null;
    return formatPercent(v);
  });

  protected readonly revenueDisplay = computed(() => {
    const v = this.kpiQuery.data()?.revenue30dTotal;
    if (v === null || v === undefined) return null;
    return formatMoney(v);
  });

  protected readonly avgMarginTrendDir = computed(() => {
    const t = this.kpiQuery.data()?.avgMarginTrend;
    if (t === null || t === undefined || t === 0) return 'neutral' as const;
    return t > 0 ? 'up' as const : 'down' as const;
  });

  protected readonly revenueTrendDir = computed(() => {
    const t = this.kpiQuery.data()?.revenue30dTrend;
    if (t === null || t === undefined || t === 0) return 'neutral' as const;
    return t > 0 ? 'up' as const : 'down' as const;
  });
}
