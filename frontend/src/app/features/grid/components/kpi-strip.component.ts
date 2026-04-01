import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { OfferApiService } from '@core/api/offer-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { KpiCardComponent } from '@shared/components/kpi-card.component';

@Component({
  selector: 'dp-kpi-strip',
  standalone: true,
  imports: [KpiCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex gap-3 bg-[var(--bg-secondary)] px-4 py-3">
      <dp-kpi-card
        [label]="'Всего товаров'"
        [value]="totalOffers()"
        [loading]="kpiQuery.isPending()"
      />
      <dp-kpi-card
        [label]="'Средняя маржа'"
        [value]="avgMarginDisplay()"
        [trend]="kpiQuery.data()?.avgMarginTrend ?? null"
        [trendDirection]="avgMarginTrendDir()"
        [loading]="kpiQuery.isPending()"
      />
      <dp-kpi-card
        [label]="'Ожидают действий'"
        [value]="kpiQuery.data()?.pendingActionsCount ?? null"
        [loading]="kpiQuery.isPending()"
      />
      <dp-kpi-card
        [label]="'Критический остаток'"
        [value]="kpiQuery.data()?.criticalStockCount ?? null"
        [loading]="kpiQuery.isPending()"
      />
      <dp-kpi-card
        [label]="'Выручка 30 дн.'"
        [value]="revenueDisplay()"
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
    return `${v.toFixed(1).replace('.', ',')}%`;
  });

  protected readonly revenueDisplay = computed(() => {
    const v = this.kpiQuery.data()?.revenue30dTotal;
    if (v === null || v === undefined) return null;
    const formatted = Math.floor(v).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '\u00A0');
    return `${formatted}\u00A0₽`;
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
