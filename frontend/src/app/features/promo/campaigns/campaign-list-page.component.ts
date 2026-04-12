import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom, startWith } from 'rxjs';

import { Zap, Calendar, Package, Clock } from 'lucide-angular';

import { PromoApiService } from '@core/api/promo-api.service';
import { formatDateTime, renderBadge } from '@shared/utils/format.utils';
import { platformColumn } from '@shared/utils/column-factories';
import {
  CampaignStatus,
  PromoCampaignFilter,
  PromoCampaignSummary,
} from '@core/models';
import { createListPageState } from '@shared/utils/list-page-state';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { KpiCardComponent } from '@shared/components/kpi-card.component';

const CAMPAIGN_STATUS_COLOR: Record<CampaignStatus, string> = {
  UPCOMING: 'info',
  ACTIVE: 'success',
  ENDED: 'neutral',
};

const CAMPAIGN_STATUSES: CampaignStatus[] = [
  'UPCOMING', 'ACTIVE', 'ENDED',
];


@Component({
  selector: 'dp-campaign-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    FilterBarComponent,
    DataGridComponent,
    EmptyStateComponent,
    KpiCardComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <div class="flex h-full flex-col">
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <h2 class="text-base font-semibold text-[var(--text-primary)]">
          {{ 'promo.campaigns.title' | translate }}
        </h2>
      </div>

      <div class="flex flex-wrap gap-3 px-4 pt-3">
        <dp-kpi-card
          [label]="'promo.campaigns.kpi.active' | translate"
          [value]="kpiActive()"
          [icon]="ZapIcon"
          accent="success"
          [loading]="kpiQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'promo.campaigns.kpi.upcoming' | translate"
          [value]="kpiUpcoming()"
          [icon]="CalendarIcon"
          accent="info"
          [loading]="kpiQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'promo.campaigns.kpi.products_participating' | translate"
          [value]="kpiProductsParticipating()"
          [icon]="PackageIcon"
          accent="primary"
          [loading]="kpiQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'promo.campaigns.kpi.pending_decisions' | translate"
          [value]="kpiPendingDecisions()"
          [icon]="ClockIcon"
          accent="warning"
          [loading]="kpiQuery.isPending()"
        />
      </div>

      <div class="px-4 pt-2">
        <dp-filter-bar
          [filters]="filterConfigs"
          [values]="listState.filterValues()"
          (filtersChanged)="listState.onFiltersChanged($event)"
        />
      </div>

      <div class="flex-1 px-4 py-2">
        @if (campaignsQuery.isError()) {
          <dp-empty-state
            [message]="'promo.campaigns.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="campaignsQuery.refetch()"
          />
        } @else if (!campaignsQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="listState.hasActiveFilters()
              ? ('promo.campaigns.empty_filtered' | translate)
              : ('promo.campaigns.empty' | translate)"
            [actionLabel]="listState.hasActiveFilters()
              ? ('filter_bar.reset_all' | translate)
              : ''"
            (action)="listState.resetFilters()"
          />
        } @else {
          <dp-data-grid
            viewStateKey="promo:campaigns"
            [columnDefs]="columnDefs()"
            [rowData]="rows()"
            [loading]="campaignsQuery.isPending()"
            [pagination]="true"
            [pageSize]="50"
            [getRowId]="getRowId"
            [height]="'100%'"
            [initialSortModel]="listState.initialSortModel()"
            (sortChanged)="listState.onSortChanged($event)"
          />
        }
      </div>
    </div>
  `,
})
export class CampaignListPageComponent {
  private readonly promoApi = inject(PromoApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  protected readonly ZapIcon = Zap;
  protected readonly CalendarIcon = Calendar;
  protected readonly PackageIcon = Package;
  protected readonly ClockIcon = Clock;

  readonly listState = createListPageState({
    pageKey: 'promo:campaigns',
    defaultSort: { column: 'dateFrom', direction: 'desc' },
    defaultPageSize: 50,
  });

  constructor() {
    this.listState.filterValues.set({ status: ['UPCOMING', 'ACTIVE'] });
  }

  private readonly translationChange = toSignal(
    this.translate.onTranslationChange.pipe(startWith(null)),
  );

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: 'promo.campaigns.filter.status',
      type: 'multi-select',
      options: CAMPAIGN_STATUSES.map((value) => ({
        value,
        label: `promo.campaigns.status.${value}`,
      })),
    },
    {
      key: 'marketplaceType',
      label: 'promo.campaigns.filter.marketplace',
      type: 'multi-select',
      options: [
        { value: 'WB', label: 'onboarding.connection.wb' },
        { value: 'OZON', label: 'onboarding.connection.ozon' },
      ],
    },
    {
      key: 'promoType',
      label: 'promo.campaigns.filter.type',
      type: 'text',
    },
    {
      key: 'search',
      label: 'promo.campaigns.filter.search',
      type: 'text',
    },
  ];

  readonly columnDefs = computed(() => {
    this.translationChange();
    return [
      {
        headerName: this.translate.instant('promo.campaigns.col.name'),
        field: 'promoName',
        minWidth: 250,
        flex: 1,
        pinned: 'left' as const,
        sortable: true,
        cellRenderer: (params: any) => {
          if (!params.data) return '';
          return `<span class="font-medium text-[var(--accent-primary)] cursor-pointer hover:underline" title="${params.data.promoName}">${params.data.promoName}</span>`;
        },
        onCellClicked: (params: any) => {
          if (params.data) this.onRowClicked(params.data);
        },
      },
      platformColumn(this.translate, 'sourcePlatform', 'promo.campaigns.col.marketplace', 100),
      {
        headerName: this.translate.instant('promo.campaigns.col.type'),
        field: 'promoType',
        tooltipField: 'promoType',
        width: 140,
        sortable: true,
        valueFormatter: (params: any) =>
          params.value ? this.translate.instant('promo.promo_type.' + params.value) : '—',
      },
      {
        headerName: this.translate.instant('promo.campaigns.col.mechanic'),
        headerTooltip: this.translate.instant('promo.campaigns.col.mechanic'),
        field: 'mechanic',
        width: 120,
        sortable: true,
        valueFormatter: (params: any) =>
          params.value ? this.translate.instant('promo.mechanic.' + params.value) : '—',
      },
      {
        headerName: this.translate.instant('promo.campaigns.col.date_from'),
        headerTooltip: this.translate.instant('promo.campaigns.col.date_from'),
        field: 'dateFrom',
        width: 110,
        sortable: true,
        valueFormatter: (params: any) => this.formatDate(params.value),
      },
      {
        headerName: this.translate.instant('promo.campaigns.col.date_to'),
        headerTooltip: this.translate.instant('promo.campaigns.col.date_to'),
        field: 'dateTo',
        width: 110,
        sortable: true,
        valueFormatter: (params: any) =>
          params.value
            ? this.formatDate(params.value)
            : this.translate.instant('promo.campaigns.indefinite'),
      },
      {
        headerName: this.translate.instant('promo.campaigns.col.eligible'),
        headerTooltip: this.translate.instant('promo.campaigns.col.eligible'),
        field: 'eligibleCount',
        width: 100,
        sortable: true,
        cellClass: 'font-mono text-right',
        valueFormatter: (params: any) => this.formatNumber(params.value),
      },
      {
        headerName: this.translate.instant('promo.campaigns.col.participating'),
        headerTooltip: this.translate.instant('promo.campaigns.col.participating'),
        field: 'participatedCount',
        width: 100,
        sortable: true,
        cellClass: 'font-mono text-right',
        valueFormatter: (params: any) => this.formatNumber(params.value),
      },
      {
        headerName: this.translate.instant('promo.campaigns.col.freeze_at'),
        headerTooltip: this.translate.instant('promo.campaigns.col.freeze_at'),
        field: 'freezeAt',
        width: 110,
        sortable: true,
        valueFormatter: (params: any) =>
          params.value ? this.formatDate(params.value) : '—',
      },
      {
        headerName: this.translate.instant('promo.campaigns.col.connection'),
        field: 'connectionName',
        tooltipField: 'connectionName',
        width: 140,
        sortable: true,
      },
      {
        headerName: this.translate.instant('promo.campaigns.col.status'),
        headerTooltip: this.translate.instant('promo.campaigns.col.status'),
        field: 'status',
        width: 130,
        sortable: true,
        cellRenderer: (params: any) => {
          const st = params.value as CampaignStatus;
          const label = this.translate.instant(`promo.campaigns.status.${st}`);
          const color = CAMPAIGN_STATUS_COLOR[st] ?? 'neutral';
          return renderBadge(label, `var(--status-${color})`);
        },
      },
    ];
  });

  private readonly filter = computed<PromoCampaignFilter>(() => {
    const vals = this.listState.filterValues();
    const f: PromoCampaignFilter = {};
    if (vals['status']?.length) f.status = vals['status'];
    if (vals['marketplaceType']?.length) f.marketplaceType = vals['marketplaceType'];
    if (vals['promoType']) f.promoType = vals['promoType'];
    if (vals['search']) f.search = vals['search'];
    return f;
  });

  readonly campaignsQuery = injectQuery(() => ({
    queryKey: [
      'promo-campaigns',
      this.wsStore.currentWorkspaceId(),
      this.filter(),
      this.listState.currentPage(),
      this.listState.sortParam(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.promoApi.listCampaigns(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.listState.currentPage(),
          50,
          this.listState.sortParam(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.campaignsQuery.data()?.content ?? []);

  readonly kpiQuery = injectQuery(() => ({
    queryKey: ['promo-campaigns-kpi', this.wsStore.currentWorkspaceId()],
    queryFn: () =>
      lastValueFrom(this.promoApi.getCampaignKpi(this.wsStore.currentWorkspaceId()!)),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly kpiActive = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi ? kpi.activeCount.toLocaleString('ru-RU') : null;
  });

  readonly kpiUpcoming = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi ? kpi.upcomingCount.toLocaleString('ru-RU') : null;
  });

  readonly kpiProductsParticipating = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi ? kpi.productsParticipating.toLocaleString('ru-RU') : null;
  });

  readonly kpiPendingDecisions = computed(() => 0);

  readonly getRowId = (params: any) => String(params.data.id);

  onRowClicked(row: PromoCampaignSummary): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'promo', 'campaigns', row.id]);
  }

  private formatDate(iso: string | null): string {
    return formatDateTime(iso, 'date');
  }

  private formatNumber(val: number | null): string {
    if (val === null || val === undefined) return '—';
    return val.toLocaleString('ru-RU');
  }
}
