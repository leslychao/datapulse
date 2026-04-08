import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom, startWith } from 'rxjs';

import { Zap, Calendar, Package, Clock } from 'lucide-angular';

import { PromoApiService } from '@core/api/promo-api.service';
import { formatDateTime, renderBadge } from '@shared/utils/format.utils';
import {
  CampaignStatus,
  PromoCampaignFilter,
  PromoCampaignSummary,
} from '@core/models';
import { SortUrlState, readSortFromUrl, syncSortToUrl } from '@shared/utils/url-filters';
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

const MP_BADGE: Record<
  string,
  { bg: string; color: string; borderColor: string; label: string }
> = {
  WB: {
    bg: 'var(--mp-wb-bg)',
    color: 'var(--mp-wb)',
    borderColor: 'var(--mp-wb)',
    label: 'WB',
  },
  OZON: {
    bg: 'var(--mp-ozon-bg)',
    color: 'var(--mp-ozon)',
    borderColor: 'var(--mp-ozon)',
    label: 'Ozon',
  },
};

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
          [values]="filterValues()"
          (filtersChanged)="onFiltersChanged($event)"
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
            [message]="hasActiveFilters()
              ? ('promo.campaigns.empty_filtered' | translate)
              : ('promo.campaigns.empty' | translate)"
            [actionLabel]="hasActiveFilters()
              ? ('filter_bar.reset_all' | translate)
              : ''"
            (action)="onFiltersChanged({})"
          />
        } @else {
          <dp-data-grid
            [columnDefs]="columnDefs()"
            [rowData]="rows()"
            [loading]="campaignsQuery.isPending()"
            [pagination]="true"
            [pageSize]="50"
            [getRowId]="getRowId"
            [height]="'100%'"
            [initialSortModel]="initialSortModel()"
            (sortChanged)="onSortChanged($event)"
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
  private readonly route = inject(ActivatedRoute);
  private readonly translate = inject(TranslateService);

  protected readonly ZapIcon = Zap;
  protected readonly CalendarIcon = Calendar;
  protected readonly PackageIcon = Package;
  protected readonly ClockIcon = Clock;

  readonly filterValues = signal<Record<string, any>>({
    status: ['UPCOMING', 'ACTIVE'],
  });
  readonly currentPage = signal(0);
  readonly currentSort = signal<SortUrlState>({ column: 'dateFrom', direction: 'desc' });
  private readonly sortString = computed(() => `${this.currentSort().column},${this.currentSort().direction}`);
  readonly initialSortModel = computed(() => {
    const s = this.currentSort();
    return s.column ? [{ colId: s.column, sort: s.direction }] : [];
  });

  constructor() {
    readSortFromUrl(this.route, this.currentSort);
    syncSortToUrl(this.router, this.route, this.currentSort, { column: 'dateFrom', direction: 'desc' });
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
      {
        headerName: this.translate.instant('promo.campaigns.col.marketplace'),
        headerTooltip: this.translate.instant('promo.campaigns.col.marketplace'),
        field: 'sourcePlatform',
        width: 100,
        cellClass: 'text-center',
        cellRenderer: (params: any) => {
          const mp = MP_BADGE[params.value];
          if (!mp) return params.value ?? '';
          return `<span class="inline-flex items-center rounded px-2 py-0.5 text-[11px] font-bold" style="background-color:${mp.bg};color:${mp.color};border:1px solid ${mp.borderColor}">${mp.label}</span>`;
        },
      },
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
    const vals = this.filterValues();
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
      this.currentPage(),
      this.sortString(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.promoApi.listCampaigns(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.currentPage(),
          50,
          this.sortString(),
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

  readonly hasActiveFilters = computed(() =>
    Object.values(this.filterValues()).some(
      (v) =>
        v !== '' &&
        v !== null &&
        v !== undefined &&
        (!Array.isArray(v) || v.length > 0),
    ),
  );

  readonly getRowId = (params: any) => String(params.data.id);

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
    this.currentPage.set(0);
  }

  onSortChanged(sort: { column: string; direction: string }): void {
    if (sort.column) {
      this.currentSort.set({ column: sort.column, direction: sort.direction as 'asc' | 'desc' });
    } else {
      this.currentSort.set({ column: 'dateFrom', direction: 'desc' });
    }
    this.currentPage.set(0);
  }

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
