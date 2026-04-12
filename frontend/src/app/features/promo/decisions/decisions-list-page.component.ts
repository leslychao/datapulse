import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom, startWith } from 'rxjs';

import { CheckCircle, XCircle, Clock } from 'lucide-angular';

import { PromoApiService } from '@core/api/promo-api.service';
import { formatDateTime, formatMoney, renderBadge } from '@shared/utils/format.utils';
import {
  PromoDecisionFilter,
  PromoDecisionType,
} from '@core/models';
import { createListPageState } from '@shared/utils/list-page-state';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { DetailPanelService } from '@shared/services/detail-panel.service';

const DECISION_COLOR: Record<PromoDecisionType, string> = {
  PARTICIPATE: 'success',
  DECLINE: 'neutral',
  DEACTIVATE: 'error',
  PENDING_REVIEW: 'warning',
};

const DECISION_TYPES: PromoDecisionType[] = [
  'PARTICIPATE', 'DECLINE', 'DEACTIVATE', 'PENDING_REVIEW',
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
  selector: 'dp-decisions-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-1 flex-col min-h-0' },
  imports: [
    TranslatePipe,
    FilterBarComponent,
    DataGridComponent,
    EmptyStateComponent,
    KpiCardComponent,
  ],
  template: `
    <div class="flex h-full flex-col">
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <h2 class="text-base font-semibold text-[var(--text-primary)]">
          {{ 'promo.decisions.title' | translate }}
        </h2>
      </div>

      <div class="flex flex-wrap gap-3 px-4 pt-3">
        <dp-kpi-card
          [label]="'promo.decisions.kpi.participate' | translate"
          [value]="kpiParticipate()"
          [icon]="CheckCircleIcon"
          accent="success"
          [loading]="kpiQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'promo.decisions.kpi.decline' | translate"
          [value]="kpiDecline()"
          [icon]="XCircleIcon"
          accent="neutral"
          [loading]="kpiQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'promo.decisions.kpi.pending_review' | translate"
          [value]="kpiPendingReview()"
          [icon]="ClockIcon"
          accent="warning"
          [loading]="kpiQuery.isPending()"
        />
      </div>

      <div class="px-6 pt-2">
        <dp-filter-bar
          [filters]="filterConfigs"
          [values]="listState.filterValues()"
          (filtersChanged)="listState.onFiltersChanged($event)"
        />
      </div>

      <div class="flex-1 px-4 py-2">
        @if (decisionsQuery.isError()) {
          <dp-empty-state
            [message]="'promo.decisions.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="decisionsQuery.refetch()"
          />
        } @else if (!decisionsQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="listState.hasActiveFilters()
              ? ('promo.decisions.empty_filtered' | translate)
              : ('promo.decisions.empty' | translate)"
            [actionLabel]="listState.hasActiveFilters()
              ? ('filter_bar.reset_all' | translate)
              : ''"
            (action)="listState.resetFilters()"
          />
        } @else {
          <dp-data-grid
            viewStateKey="promo:decisions"
            [columnDefs]="columnDefs()"
            [rowData]="rows()"
            [loading]="decisionsQuery.isPending()"
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
export class DecisionsListPageComponent {
  private readonly promoApi = inject(PromoApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly translate = inject(TranslateService);
  private readonly detailPanel = inject(DetailPanelService);

  protected readonly CheckCircleIcon = CheckCircle;
  protected readonly XCircleIcon = XCircle;
  protected readonly ClockIcon = Clock;

  readonly listState = createListPageState({
    pageKey: 'promo:decisions',
    defaultSort: { column: 'createdAt', direction: 'desc' },
    defaultPageSize: 50,
  });

  private readonly translationChange = toSignal(
    this.translate.onTranslationChange.pipe(startWith(null)),
  );

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'decisionType',
      label: 'promo.decisions.col.decision',
      type: 'multi-select',
      options: DECISION_TYPES.map((value) => ({
        value,
        label: `promo.decision_type.${value}`,
      })),
    },
    {
      key: 'decidedBy',
      label: 'promo.decisions.filter.decided_by',
      type: 'select',
      options: [
        { value: 'all', label: 'promo.decisions.filter.decided_by_all' },
        { value: 'auto', label: 'promo.decisions.filter.decided_by_auto' },
        { value: 'manual', label: 'promo.decisions.filter.decided_by_manual' },
      ],
    },
    { key: 'search', label: 'promo.decisions.filter.search', type: 'text' },
  ];

  readonly columnDefs = computed(() => {
    this.translationChange();
    return [
      {
        headerName: this.translate.instant('promo.decisions.col.campaign'),
        field: 'campaignName',
        minWidth: 200,
        sortable: true,
        cellRenderer: (params: any) => {
          if (!params.data) return '';
          return `<span class="font-medium text-[var(--accent-primary)] cursor-pointer hover:underline" title="${params.data.campaignName}">${params.data.campaignName}</span>`;
        },
        onCellClicked: (params: any) => {
          if (params.data) this.onRowClicked(params.data);
        },
      },
      {
        headerName: this.translate.instant('promo.decisions.col.marketplace'),
        headerTooltip: this.translate.instant('promo.decisions.col.marketplace'),
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
        headerName: this.translate.instant('promo.decisions.col.product'),
        field: 'productName',
        minWidth: 230,
        sortable: true,
      },
      {
        headerName: this.translate.instant('promo.decisions.col.sku'),
        headerTooltip: this.translate.instant('promo.decisions.col.sku'),
        field: 'marketplaceSku',
        tooltipField: 'marketplaceSku',
        width: 120,
        cellClass: 'font-mono',
      },
      {
        headerName: this.translate.instant('promo.decisions.col.decision'),
        headerTooltip: this.translate.instant('promo.decisions.col.decision'),
        field: 'decisionType',
        width: 130,
        sortable: true,
        cellRenderer: (params: any) => this.badgeCell(params.value, 'promo.decision_type', DECISION_COLOR),
      },
      {
        headerName: this.translate.instant('promo.decisions.col.mode'),
        headerTooltip: this.translate.instant('promo.decisions.col.mode'),
        field: 'participationMode',
        width: 120,
        valueFormatter: (params: any) =>
          params.value
            ? this.translate.instant(`promo.participation_mode.${params.value}`)
            : '—',
      },
      {
        headerName: this.translate.instant('promo.decisions.col.target_price'),
        headerTooltip: this.translate.instant('promo.decisions.col.target_price'),
        field: 'targetPromoPrice',
        width: 130,
        cellClass: 'font-mono text-right',
        valueFormatter: (params: any) => formatMoney(params.value),
      },
      {
        headerName: this.translate.instant('promo.decisions.col.explanation'),
        field: 'explanationSummary',
        minWidth: 250,
        cellRenderer: (params: any) => {
          if (!params.value) return '—';
          const truncated = params.value.length > 80
            ? params.value.substring(0, 80) + '…'
            : params.value;
          return `<span title="${params.value}" class="text-[var(--text-secondary)]">${truncated}</span>`;
        },
      },
      {
        headerName: this.translate.instant('promo.decisions.col.decided_by'),
        headerTooltip: this.translate.instant('promo.decisions.col.decided_by'),
        field: 'decidedByName',
        tooltipField: 'decidedByName',
        width: 130,
        valueFormatter: (params: any) =>
          params.value ?? this.translate.instant('promo.decisions.col.decided_by_auto'),
      },
      {
        headerName: this.translate.instant('promo.decisions.col.policy'),
        field: 'policyName',
        width: 160,
        tooltipValueGetter: (params: any) => {
          if (!params.data) return '';
          const name = params.data.policyName ?? '';
          const ver = params.data.policyVersion != null ? ` v${params.data.policyVersion}` : '';
          return `${name}${ver}`;
        },
        cellRenderer: (params: any) => {
          if (!params.data) return '';
          const name = params.data.policyName ?? '';
          const ver = params.data.policyVersion != null ? ` v${params.data.policyVersion}` : '';
          return `${name}${ver}`;
        },
      },
      {
        headerName: this.translate.instant('promo.decisions.col.date'),
        headerTooltip: this.translate.instant('promo.decisions.col.date'),
        field: 'createdAt',
        width: 130,
        sortable: true,
        valueFormatter: (params: any) => formatDateTime(params.value, 'full'),
      },
    ];
  });

  private readonly filter = computed<PromoDecisionFilter>(() => {
    const vals = this.listState.filterValues();
    const f: PromoDecisionFilter = {};
    if (vals['decisionType']?.length) f.decisionType = vals['decisionType'];
    if (vals['decidedBy'] && vals['decidedBy'] !== 'all') f.decidedBy = vals['decidedBy'];
    if (vals['search']) f.search = vals['search'];
    return f;
  });

  readonly decisionsQuery = injectQuery(() => ({
    queryKey: [
      'promo-decisions',
      this.wsStore.currentWorkspaceId(),
      this.filter(),
      this.listState.currentPage(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.promoApi.listDecisions(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.listState.currentPage(),
          50,
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.decisionsQuery.data()?.content ?? []);

  readonly kpiQuery = injectQuery(() => ({
    queryKey: ['promo-decisions-kpi', this.wsStore.currentWorkspaceId()],
    queryFn: () =>
      lastValueFrom(this.promoApi.getDecisionKpi(this.wsStore.currentWorkspaceId()!)),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly kpiParticipate = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi ? kpi.participateCount.toLocaleString('ru-RU') : null;
  });
  readonly kpiDecline = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi ? kpi.declineCount.toLocaleString('ru-RU') : null;
  });
  readonly kpiPendingReview = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi ? kpi.pendingReviewCount.toLocaleString('ru-RU') : null;
  });

  readonly getRowId = (params: any) => String(params.data.id);

  onRowClicked(row: any): void {
    this.detailPanel.open('promo-decision', row.id);
  }

  private badgeCell(
    value: string | null,
    i18nPrefix: string,
    colors: Record<string, string>,
  ): string {
    if (!value) return '';
    const label = this.translate.instant(`${i18nPrefix}.${value}`);
    const color = colors[value] ?? 'neutral';
    const cssVar = `var(--status-${color})`;
    return renderBadge(label, cssVar);
  }
}
