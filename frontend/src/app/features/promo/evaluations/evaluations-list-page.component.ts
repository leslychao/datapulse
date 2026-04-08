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

import { ClipboardList, TrendingUp, AlertCircle, TrendingDown } from 'lucide-angular';

import { PromoApiService } from '@core/api/promo-api.service';
import { formatMoney, formatDateTime, renderBadge } from '@shared/utils/format.utils';
import { EvaluationResult, PromoEvaluationFilter } from '@core/models';
import { createListPageState } from '@shared/utils/list-page-state';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { DetailPanelService } from '@shared/services/detail-panel.service';

const EVAL_COLOR: Record<EvaluationResult, string> = {
  PROFITABLE: 'success',
  MARGINAL: 'warning',
  UNPROFITABLE: 'error',
  INSUFFICIENT_STOCK: 'warning',
  INSUFFICIENT_DATA: 'warning',
};

const EVAL_VALUES: EvaluationResult[] = [
  'PROFITABLE', 'MARGINAL', 'UNPROFITABLE', 'INSUFFICIENT_STOCK', 'INSUFFICIENT_DATA',
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
  selector: 'dp-evaluations-list-page',
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
          {{ 'promo.evaluations.title' | translate }}
        </h2>
      </div>

      <div class="flex flex-wrap gap-3 px-4 pt-3">
        <dp-kpi-card
          [label]="'promo.evaluations.kpi.total' | translate"
          [value]="kpiTotal()"
          [icon]="ClipboardListIcon"
          accent="primary"
          [loading]="kpiQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'promo.evaluations.kpi.profitable' | translate"
          [value]="kpiProfitable()"
          [icon]="TrendingUpIcon"
          accent="success"
          [loading]="kpiQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'promo.evaluations.kpi.marginal' | translate"
          [value]="kpiMarginal()"
          [icon]="AlertCircleIcon"
          accent="warning"
          [loading]="kpiQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'promo.evaluations.kpi.unprofitable' | translate"
          [value]="kpiUnprofitable()"
          [icon]="TrendingDownIcon"
          accent="error"
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
        @if (evalsQuery.isError()) {
          <dp-empty-state
            [message]="'promo.evaluations.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="evalsQuery.refetch()"
          />
        } @else if (!evalsQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="listState.hasActiveFilters()
              ? ('promo.evaluations.empty_filtered' | translate)
              : ('promo.evaluations.empty' | translate)"
            [actionLabel]="listState.hasActiveFilters()
              ? ('filter_bar.reset_all' | translate)
              : ''"
            (action)="listState.resetFilters()"
          />
        } @else {
          <dp-data-grid
            viewStateKey="promo:evaluations"
            [columnDefs]="columnDefs()"
            [rowData]="rows()"
            [loading]="evalsQuery.isPending()"
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
export class EvaluationsListPageComponent {
  private readonly promoApi = inject(PromoApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly translate = inject(TranslateService);
  private readonly detailPanel = inject(DetailPanelService);

  protected readonly ClipboardListIcon = ClipboardList;
  protected readonly TrendingUpIcon = TrendingUp;
  protected readonly AlertCircleIcon = AlertCircle;
  protected readonly TrendingDownIcon = TrendingDown;

  readonly listState = createListPageState({
    pageKey: 'promo:evaluations',
    defaultSort: { column: 'evaluatedAt', direction: 'desc' },
    defaultPageSize: 50,
  });

  private readonly translationChange = toSignal(
    this.translate.onTranslationChange.pipe(startWith(null)),
  );

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'evaluationResult',
      label: 'promo.evaluations.filter.result',
      type: 'multi-select',
      options: EVAL_VALUES.map((value) => ({
        value,
        label: `promo.evaluation_result.${value}`,
      })),
    },
    {
      key: 'marketplaceType',
      label: 'promo.evaluations.filter.marketplace',
      type: 'multi-select',
      options: [
        { value: 'WB', label: 'onboarding.connection.wb' },
        { value: 'OZON', label: 'onboarding.connection.ozon' },
      ],
    },
    { key: 'search', label: 'promo.evaluations.filter.search', type: 'text' },
  ];

  readonly columnDefs = computed(() => {
    this.translationChange();
    return [
      {
        headerName: this.translate.instant('promo.evaluations.col.campaign'),
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
        headerName: this.translate.instant('promo.evaluations.col.marketplace'),
        headerTooltip: this.translate.instant('promo.evaluations.col.marketplace'),
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
        headerName: this.translate.instant('promo.evaluations.col.product'),
        field: 'productName',
        minWidth: 230,
        sortable: true,
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.sku'),
        headerTooltip: this.translate.instant('promo.evaluations.col.sku'),
        field: 'marketplaceSku',
        tooltipField: 'marketplaceSku',
        width: 120,
        cellClass: 'font-mono',
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.promo_price'),
        headerTooltip: this.translate.instant('promo.evaluations.col.promo_price'),
        field: 'promoPrice',
        width: 100,
        cellClass: 'font-mono text-right',
        sortable: true,
        valueFormatter: (params: any) => formatMoney(params.value),
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.regular_price'),
        headerTooltip: this.translate.instant('promo.evaluations.col.regular_price'),
        field: 'regularPrice',
        width: 110,
        cellClass: 'font-mono text-right',
        valueFormatter: (params: any) => formatMoney(params.value),
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.discount'),
        headerTooltip: this.translate.instant('promo.evaluations.col.discount'),
        field: 'discountPct',
        width: 80,
        cellClass: 'font-mono text-right',
        sortable: true,
        valueFormatter: (params: any) =>
          params.value != null ? `${params.value.toFixed(1).replace('.', ',')}%` : '—',
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.cogs'),
        headerTooltip: this.translate.instant('promo.evaluations.col.cogs'),
        field: 'cogs',
        width: 100,
        cellClass: 'font-mono text-right',
        valueFormatter: (params: any) => formatMoney(params.value),
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.margin_promo'),
        headerTooltip: this.translate.instant('promo.evaluations.col.margin_promo'),
        field: 'marginAtPromoPrice',
        width: 110,
        cellClass: 'font-mono text-right',
        sortable: true,
        cellRenderer: (params: any) => {
          if (params.value == null) return '—';
          const color = params.value >= 0 ? 'var(--finance-positive)' : 'var(--finance-negative)';
          return `<span style="color:${color}">${params.value.toFixed(1).replace('.', ',')}%</span>`;
        },
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.margin_regular'),
        headerTooltip: this.translate.instant('promo.evaluations.col.margin_regular'),
        field: 'marginAtRegularPrice',
        width: 110,
        cellClass: 'font-mono text-right',
        cellRenderer: (params: any) => {
          if (params.value == null) return '—';
          const color = params.value >= 0 ? 'var(--finance-positive)' : 'var(--finance-negative)';
          return `<span style="color:${color}">${params.value.toFixed(1).replace('.', ',')}%</span>`;
        },
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.margin_delta'),
        headerTooltip: this.translate.instant('promo.evaluations.col.margin_delta'),
        field: 'marginDeltaPct',
        width: 100,
        cellClass: 'font-mono text-right',
        cellRenderer: (params: any) => {
          if (params.value == null) return '—';
          const arrow = params.value < 0 ? '↓' : '↑';
          const color = params.value < 0 ? 'var(--finance-negative)' : 'var(--finance-positive)';
          return `<span style="color:${color}">${arrow} ${Math.abs(params.value).toFixed(1).replace('.', ',')} п.п.</span>`;
        },
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.stock'),
        headerTooltip: this.translate.instant('promo.evaluations.col.stock'),
        field: 'stockAvailable',
        width: 80,
        cellClass: 'font-mono text-right',
        sortable: true,
        valueFormatter: (params: any) =>
          params.value != null ? params.value.toLocaleString('ru-RU') : '—',
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.stock_days'),
        headerTooltip: this.translate.instant('promo.evaluations.col.stock_days'),
        field: 'stockDaysOfCover',
        width: 90,
        cellClass: 'font-mono text-right',
        valueFormatter: (params: any) =>
          params.value != null ? `${params.value}` : '—',
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.stock_sufficient'),
        headerTooltip: this.translate.instant('promo.evaluations.col.stock_sufficient'),
        field: 'stockSufficient',
        width: 100,
        cellRenderer: (params: any) => {
          if (params.value == null) return '—';
          const ok = params.value === true;
          const cssVar = ok ? 'var(--status-success)' : 'var(--status-warning)';
          const label = ok ? '✓' : '✗';
          return `<span style="color:${cssVar}" class="font-bold">${label}</span>`;
        },
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.result'),
        field: 'evaluationResult',
        width: 140,
        sortable: true,
        cellRenderer: (params: any) => this.badgeCell(params.value, 'promo.evaluation_result', EVAL_COLOR),
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.skip_reason'),
        field: 'skipReason',
        width: 150,
        cellRenderer: (params: any) => {
          if (!params.value) return '';
          return `<span class="text-[var(--text-secondary)]" title="${params.value}">${params.value}</span>`;
        },
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.policy'),
        field: 'policyName',
        tooltipField: 'policyName',
        width: 150,
        sortable: true,
      },
      {
        headerName: this.translate.instant('promo.evaluations.col.evaluated_at'),
        headerTooltip: this.translate.instant('promo.evaluations.col.evaluated_at'),
        field: 'evaluatedAt',
        width: 130,
        sortable: true,
        valueFormatter: (params: any) => formatDateTime(params.value, 'full'),
      },
    ];
  });

  private readonly filter = computed<PromoEvaluationFilter>(() => {
    const vals = this.listState.filterValues();
    const f: PromoEvaluationFilter = {};
    if (vals['evaluationResult']?.length) f.evaluationResult = vals['evaluationResult'];
    if (vals['marketplaceType']?.length) f.marketplaceType = vals['marketplaceType'];
    if (vals['search']) f.search = vals['search'];
    return f;
  });

  readonly evalsQuery = injectQuery(() => ({
    queryKey: [
      'promo-evaluations',
      this.wsStore.currentWorkspaceId(),
      this.filter(),
      this.listState.currentPage(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.promoApi.listEvaluations(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.listState.currentPage(),
          50,
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.evalsQuery.data()?.content ?? []);

  readonly kpiQuery = injectQuery(() => ({
    queryKey: ['promo-evaluations-kpi', this.wsStore.currentWorkspaceId()],
    queryFn: () =>
      lastValueFrom(this.promoApi.getEvaluationKpi(this.wsStore.currentWorkspaceId()!)),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly kpiTotal = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi ? kpi.total.toLocaleString('ru-RU') : null;
  });
  readonly kpiProfitable = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi ? kpi.profitableCount.toLocaleString('ru-RU') : null;
  });
  readonly kpiMarginal = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi ? kpi.marginalCount.toLocaleString('ru-RU') : null;
  });
  readonly kpiUnprofitable = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi ? kpi.unprofitableCount.toLocaleString('ru-RU') : null;
  });

  readonly getRowId = (params: any) => String(params.data.id);

  onRowClicked(row: any): void {
    this.detailPanel.open('promo-evaluation', row.id);
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
