import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PromoApiService } from '@core/api/promo-api.service';
import { formatMoney, formatDateTime } from '@shared/utils/format.utils';
import { EvaluationResult, PromoEvaluationFilter } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { KpiCardComponent } from '@shared/components/kpi-card.component';

const EVAL_LABEL: Record<EvaluationResult, string> = {
  PROFITABLE: 'Прибыльно',
  MARGINAL: 'Пограничный',
  UNPROFITABLE: 'Убыточно',
  INSUFFICIENT_STOCK: 'Мало остатков',
  INSUFFICIENT_DATA: 'Нет данных',
};

const EVAL_COLOR: Record<EvaluationResult, string> = {
  PROFITABLE: 'success',
  MARGINAL: 'warning',
  UNPROFITABLE: 'error',
  INSUFFICIENT_STOCK: 'warning',
  INSUFFICIENT_DATA: 'warning',
};

const MP_BADGE: Record<string, { bg: string; label: string }> = {
  WB: { bg: '#CB11AB', label: 'WB' },
  OZON: { bg: '#005BFF', label: 'Ozon' },
};

@Component({
  selector: 'dp-evaluations-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    FilterBarComponent,
    DataGridComponent,
    EmptyStateComponent,
    KpiCardComponent,
  ],
  template: `
    <div class="flex h-full flex-col">
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-3">
        <h2 class="text-base font-semibold text-[var(--text-primary)]">
          {{ 'promo.evaluations.title' | translate }}
        </h2>
      </div>

      <div class="flex gap-3 px-4 pt-4">
        <dp-kpi-card
          [label]="'promo.evaluations.kpi.total' | translate"
          [value]="kpiTotal()"
          [loading]="evalsQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'promo.evaluations.kpi.profitable' | translate"
          [value]="kpiProfitable()"
          [loading]="evalsQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'promo.evaluations.kpi.marginal' | translate"
          [value]="kpiMarginal()"
          [loading]="evalsQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'promo.evaluations.kpi.unprofitable' | translate"
          [value]="kpiUnprofitable()"
          [loading]="evalsQuery.isPending()"
        />
      </div>

      <div class="px-4 pt-3">
        <dp-filter-bar
          [filters]="filterConfigs"
          [values]="filterValues()"
          (filtersChanged)="onFiltersChanged($event)"
        />
      </div>

      <div class="flex-1 px-4 py-3">
        @if (evalsQuery.isError()) {
          <dp-empty-state
            [message]="'promo.evaluations.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="evalsQuery.refetch()"
          />
        } @else if (!evalsQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="hasActiveFilters()
              ? ('promo.evaluations.empty_filtered' | translate)
              : ('promo.evaluations.empty' | translate)"
            [actionLabel]="hasActiveFilters()
              ? ('filter_bar.reset_all' | translate)
              : ''"
            (action)="onFiltersChanged({})"
          />
        } @else {
          <dp-data-grid
            [columnDefs]="columnDefs"
            [rowData]="rows()"
            [loading]="evalsQuery.isPending()"
            [pagination]="true"
            [pageSize]="50"
            [getRowId]="getRowId"
            [height]="'100%'"
          />
        }
      </div>
    </div>
  `,
})
export class EvaluationsListPageComponent {
  private readonly promoApi = inject(PromoApiService);
  private readonly wsStore = inject(WorkspaceContextStore);

  readonly filterValues = signal<Record<string, any>>({});
  readonly currentPage = signal(0);

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'evaluationResult',
      label: 'Результат',
      type: 'multi-select',
      options: Object.entries(EVAL_LABEL).map(([value, label]) => ({ value, label })),
    },
    {
      key: 'marketplaceType',
      label: 'Маркетплейс',
      type: 'multi-select',
      options: [
        { value: 'WB', label: 'Wildberries' },
        { value: 'OZON', label: 'Ozon' },
      ],
    },
    { key: 'search', label: 'Поиск по товару', type: 'text' },
  ];

  readonly columnDefs = [
    {
      headerName: 'Кампания',
      field: 'campaignName',
      minWidth: 200,
      sortable: true,
      cellRenderer: (params: any) => {
        if (!params.data) return '';
        return `<span class="font-medium" title="${params.data.campaignName}">${params.data.campaignName}</span>`;
      },
    },
    {
      headerName: 'Маркетплейс',
      field: 'sourcePlatform',
      width: 100,
      cellClass: 'text-center',
      cellRenderer: (params: any) => {
        const mp = MP_BADGE[params.value];
        if (!mp) return params.value ?? '';
        return `<span class="inline-flex items-center rounded px-2 py-0.5 text-[11px] font-bold text-white" style="background:${mp.bg}">${mp.label}</span>`;
      },
    },
    {
      headerName: 'Товар',
      field: 'productName',
      minWidth: 230,
      sortable: true,
    },
    {
      headerName: 'SKU',
      field: 'marketplaceSku',
      width: 120,
      cellClass: 'font-mono',
    },
    {
      headerName: 'Промо-цена',
      field: 'promoPrice',
      width: 100,
      cellClass: 'font-mono text-right',
      sortable: true,
      valueFormatter: (params: any) => formatMoney(params.value),
    },
    {
      headerName: 'Обычная цена',
      field: 'regularPrice',
      width: 110,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: any) => formatMoney(params.value),
    },
    {
      headerName: 'Скидка',
      field: 'discountPct',
      width: 80,
      cellClass: 'font-mono text-right',
      sortable: true,
      valueFormatter: (params: any) =>
        params.value != null ? `${params.value.toFixed(1).replace('.', ',')}%` : '—',
    },
    {
      headerName: 'Маржа (промо)',
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
      headerName: 'Дельта маржи',
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
      headerName: 'Остатки',
      field: 'stockAvailable',
      width: 80,
      cellClass: 'font-mono text-right',
      sortable: true,
      valueFormatter: (params: any) =>
        params.value != null ? params.value.toLocaleString('ru-RU') : '—',
    },
    {
      headerName: 'Результат',
      field: 'evaluationResult',
      width: 140,
      sortable: true,
      cellRenderer: (params: any) => {
        const val = params.value as EvaluationResult;
        if (!val) return '';
        const label = EVAL_LABEL[val] ?? val;
        const color = EVAL_COLOR[val] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        return `<span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                  style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}">
          <span class="inline-block h-1.5 w-1.5 rounded-full" style="background-color: ${cssVar}"></span>
          ${label}
        </span>`;
      },
    },
    {
      headerName: 'Политика',
      field: 'policyName',
      width: 150,
      sortable: true,
    },
    {
      headerName: 'Оценено',
      field: 'evaluatedAt',
      width: 130,
      sortable: true,
      sort: 'desc' as const,
      valueFormatter: (params: any) => formatDateTime(params.value, 'full'),
    },
  ];

  private readonly filter = computed<PromoEvaluationFilter>(() => {
    const vals = this.filterValues();
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
      this.currentPage(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.promoApi.listEvaluations(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.currentPage(),
          50,
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.evalsQuery.data()?.content ?? []);

  readonly kpiTotal = computed(() => this.evalsQuery.data()?.totalElements ?? 0);
  readonly kpiProfitable = computed(() =>
    this.rows().filter((e) => e.evaluationResult === 'PROFITABLE').length,
  );
  readonly kpiMarginal = computed(() =>
    this.rows().filter((e) => e.evaluationResult === 'MARGINAL').length,
  );
  readonly kpiUnprofitable = computed(() =>
    this.rows().filter((e) =>
      ['UNPROFITABLE', 'INSUFFICIENT_STOCK', 'INSUFFICIENT_DATA'].includes(e.evaluationResult),
    ).length,
  );

  readonly hasActiveFilters = computed(() =>
    Object.values(this.filterValues()).some(
      (v) => v !== '' && v !== null && v !== undefined && (!Array.isArray(v) || v.length > 0),
    ),
  );

  readonly getRowId = (params: any) => String(params.data.id);

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
    this.currentPage.set(0);
  }

}
