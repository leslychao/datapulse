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

import { PricingApiService } from '@core/api/pricing-api.service';
import { formatMoney, formatDateTime } from '@shared/utils/format.utils';
import { PricingDecisionFilter, PricingDecisionSummary } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import {
  FilterBarComponent,
  FilterConfig,
} from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';

const DECISION_TYPE_COLOR: Record<string, string> = {
  CHANGE: 'success',
  SKIP: 'warning',
  HOLD: 'neutral',
};

const DECISION_TYPE_LABEL: Record<string, string> = {
  CHANGE: 'Изменение',
  SKIP: 'Пропуск',
  HOLD: 'Ожидание',
};

const STRATEGY_TYPE_LABEL: Record<string, string> = {
  TARGET_MARGIN: 'Целевая маржа',
  PRICE_CORRIDOR: 'Ценовой коридор',
};

const EXECUTION_MODE_LABEL: Record<string, string> = {
  LIVE: 'LIVE',
  SIMULATED: 'Симуляция',
};

@Component({
  selector: 'dp-decisions-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    FilterBarComponent,
    DataGridComponent,
    EmptyStateComponent,
  ],
  template: `
    <div class="flex h-full flex-col">
      <dp-filter-bar
        [filters]="filterConfigs"
        [values]="filterValues()"
        (filtersChanged)="onFiltersChanged($event)"
      />

      <div class="flex-1 px-4 py-3">
        @if (decisionsQuery.isError()) {
          <dp-empty-state
            [message]="'pricing.decisions.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="decisionsQuery.refetch()"
          />
        } @else if (!decisionsQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="hasActiveFilters()
              ? ('pricing.decisions.empty_filtered' | translate)
              : ('pricing.decisions.empty' | translate)"
            [actionLabel]="hasActiveFilters()
              ? ('filter_bar.reset_all' | translate)
              : ''"
            (action)="onFiltersChanged({})"
          />
        } @else {
          <dp-data-grid
            [columnDefs]="columnDefs"
            [rowData]="rows()"
            [loading]="decisionsQuery.isPending()"
            [pagination]="true"
            [pageSize]="100"
            [getRowId]="getRowId"
            [height]="'100%'"
          />
        }
      </div>
    </div>
  `,
})
export class DecisionsListPageComponent {
  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);

  readonly filterValues = signal<Record<string, any>>({});
  readonly currentPage = signal(0);
  readonly currentSort = signal('createdAt,desc');

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'decisionType',
      label: 'Тип решения',
      type: 'multi-select',
      options: Object.entries(DECISION_TYPE_LABEL).map(([value, label]) => ({
        value,
        label,
      })),
    },
    {
      key: 'executionMode',
      label: 'Режим',
      type: 'select',
      options: Object.entries(EXECUTION_MODE_LABEL).map(([value, label]) => ({
        value,
        label,
      })),
    },
    { key: 'period', label: 'Период', type: 'date-range' },
  ];

  readonly columnDefs = [
    {
      headerName: '#',
      field: 'id',
      width: 80,
      sortable: true,
      cellClass: 'font-mono',
    },
    {
      headerName: 'Оффер',
      field: 'offerName',
      minWidth: 220,
      flex: 1,
      sortable: true,
    },
    {
      headerName: 'SKU',
      field: 'sellerSku',
      width: 120,
      sortable: true,
      cellClass: 'font-mono',
    },
    {
      headerName: 'Подключение',
      field: 'connectionName',
      width: 160,
      sortable: true,
    },
    {
      headerName: 'Решение',
      field: 'decisionType',
      width: 130,
      sortable: true,
      cellRenderer: (params: any) => {
        const val = params.value as string;
        const label = DECISION_TYPE_LABEL[val] ?? val;
        const color = DECISION_TYPE_COLOR[val] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        return `<span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                  style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}">
          <span class="inline-block h-1.5 w-1.5 rounded-full" style="background-color: ${cssVar}"></span>
          ${label}
        </span>`;
      },
    },
    {
      headerName: 'Было',
      field: 'currentPrice',
      width: 110,
      sortable: true,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: any) => this.formatPrice(params.value),
    },
    {
      headerName: 'Стало',
      field: 'targetPrice',
      width: 110,
      sortable: true,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: any) => this.formatPrice(params.value),
    },
    {
      headerName: 'Δ%',
      field: 'changePct',
      width: 80,
      sortable: true,
      cellClass: 'font-mono text-right',
      cellRenderer: (params: any) => {
        const v = params.value;
        if (v === null || v === undefined) return '—';
        const abs = Math.abs(v).toFixed(1).replace('.', ',');
        if (v > 0)
          return `<span style="color: var(--finance-positive)">↑ ${abs}%</span>`;
        if (v < 0)
          return `<span style="color: var(--finance-negative)">↓ ${abs}%</span>`;
        return `<span style="color: var(--finance-zero)">→ 0%</span>`;
      },
    },
    {
      headerName: 'Политика',
      field: 'policyName',
      width: 160,
      sortable: true,
    },
    {
      headerName: 'Стратегия',
      field: 'strategyType',
      width: 150,
      sortable: true,
      cellRenderer: (params: any) => {
        const label = STRATEGY_TYPE_LABEL[params.value] ?? params.value;
        return `<span class="inline-flex items-center rounded-full bg-[var(--bg-tertiary)] px-2.5 py-0.5 text-[11px] font-medium text-[var(--text-secondary)]">${label}</span>`;
      },
    },
    {
      headerName: 'Режим',
      field: 'executionMode',
      width: 100,
      sortable: true,
      cellRenderer: (params: any) => {
        if (params.value === 'SIMULATED') {
          return `<span class="rounded-full border border-dashed border-[var(--border-default)] px-2 py-0.5 text-[11px] text-[var(--text-secondary)]">SIM</span>`;
        }
        return `<span class="text-[11px] text-[var(--text-primary)]">LIVE</span>`;
      },
    },
    {
      headerName: 'Прогон',
      field: 'pricingRunId',
      width: 80,
      sortable: true,
      cellClass: 'font-mono',
      cellRenderer: (params: any) => {
        if (!params.value) return '—';
        return `<span class="text-[var(--accent-primary)] cursor-pointer hover:underline">#${params.value}</span>`;
      },
    },
    {
      headerName: 'Причина пропуска',
      field: 'skipReason',
      minWidth: 200,
      flex: 1,
      sortable: false,
      valueFormatter: (params: any) => params.value ?? '—',
    },
    {
      headerName: 'Дата',
      field: 'createdAt',
      width: 140,
      sortable: true,
      sort: 'desc' as const,
      valueFormatter: (params: any) => this.formatTimestamp(params.value),
    },
  ];

  private readonly filter = computed<PricingDecisionFilter>(() => {
    const vals = this.filterValues();
    const f: PricingDecisionFilter = {};
    if (vals['decisionType']?.length) f.decisionType = vals['decisionType'];
    if (vals['executionMode']) f.executionMode = vals['executionMode'];
    if (vals['period']?.from) f.from = vals['period'].from;
    if (vals['period']?.to) f.to = vals['period'].to;
    return f;
  });

  readonly decisionsQuery = injectQuery(() => ({
    queryKey: [
      'decisions',
      this.wsStore.currentWorkspaceId(),
      this.filter(),
      this.currentPage(),
      this.currentSort(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.listDecisions(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.currentPage(),
          100,
          this.currentSort(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.decisionsQuery.data()?.content ?? []);

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

  private formatPrice(value: number | null): string {
    return formatMoney(value, 0);
  }

  private formatTimestamp(iso: string | null): string {
    return formatDateTime(iso, 'full');
  }
}
