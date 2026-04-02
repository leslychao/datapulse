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
import {
  ColDef,
  GetRowIdParams,
  ICellRendererParams,
  ValueFormatterParams,
} from 'ag-grid-community';

import { PricingApiService } from '@core/api/pricing-api.service';
import { formatMoney, formatDateTime, renderBadge } from '@shared/utils/format.utils';
import { PricingDecisionFilter, PricingDecisionSummary } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { DetailPanelService } from '@shared/services/detail-panel.service';
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
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <div class="flex h-full flex-col">
      <div class="border-b border-[var(--border-default)] px-4 py-2">
        <dp-filter-bar
          [filters]="filterConfigs"
          [values]="filterValues()"
          (filtersChanged)="onFiltersChanged($event)"
        />
      </div>

      <div class="flex-1 px-4 py-2">
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
            (rowClicked)="onRowClicked($event)"
          />
        }
      </div>
    </div>
  `,
})
export class DecisionsListPageComponent {
  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly translate = inject(TranslateService);
  private readonly detailPanel = inject(DetailPanelService);

  readonly filterValues = signal<Record<string, any>>({});
  readonly currentPage = signal(0);
  readonly currentSort = signal('createdAt,desc');

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'decisionType',
      label: 'pricing.decisions.filter.decision_type',
      type: 'multi-select',
      options: (['CHANGE', 'SKIP', 'HOLD'] as const).map(value => ({
        value,
        label: `pricing.decisions.type.${value}`,
      })),
    },
    {
      key: 'executionMode',
      label: 'pricing.decisions.filter.execution_mode',
      type: 'select',
      options: (['LIVE', 'SIMULATED'] as const).map(value => ({
        value,
        label: `pricing.decisions.execution_mode.${value}`,
      })),
    },
    {
      key: 'period',
      label: 'pricing.decisions.filter.period',
      type: 'date-range',
    },
  ];

  readonly columnDefs: ColDef[] = [
    {
      headerName: '#',
      field: 'id',
      width: 80,
      sortable: true,
      cellClass: 'font-mono',
    },
    {
      headerName: this.translate.instant('pricing.decisions.col.offer'),
      field: 'offerName',
      minWidth: 220,
      flex: 1,
      sortable: true,
    },
    {
      headerName: this.translate.instant('pricing.decisions.col.sku'),
      field: 'sellerSku',
      width: 120,
      sortable: true,
      cellClass: 'font-mono',
    },
    {
      headerName: this.translate.instant('pricing.decisions.col.connection'),
      field: 'connectionName',
      width: 160,
      sortable: true,
    },
    {
      headerName: this.translate.instant('pricing.decisions.col.decision'),
      field: 'decisionType',
      width: 130,
      sortable: true,
      cellRenderer: (params: ICellRendererParams<PricingDecisionSummary>) => {
        const val = params.value as string;
        const label = this.translate.instant(`pricing.decisions.type.${val}`);
        const color = DECISION_TYPE_COLOR[val] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        return renderBadge(label, cssVar);
      },
    },
    {
      headerName: this.translate.instant('pricing.decisions.col.current_price'),
      field: 'currentPrice',
      width: 110,
      sortable: true,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: ValueFormatterParams<PricingDecisionSummary>) =>
        this.formatPrice(params.value),
    },
    {
      headerName: this.translate.instant('pricing.decisions.col.target_price'),
      field: 'targetPrice',
      width: 110,
      sortable: true,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: ValueFormatterParams<PricingDecisionSummary>) =>
        this.formatPrice(params.value),
    },
    {
      headerName: 'Δ%',
      field: 'changePct',
      width: 80,
      sortable: true,
      cellClass: 'font-mono text-right',
      cellRenderer: (params: ICellRendererParams<PricingDecisionSummary>) => {
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
      headerName: this.translate.instant('pricing.decisions.col.policy'),
      field: 'policyName',
      width: 160,
      sortable: true,
    },
    {
      headerName: this.translate.instant('pricing.decisions.col.strategy'),
      field: 'strategyType',
      width: 150,
      sortable: true,
      cellRenderer: (params: ICellRendererParams<PricingDecisionSummary>) => {
        const label = this.translate.instant(
          `pricing.policies.strategy.${params.value}`,
        );
        return `<span class="inline-flex items-center rounded-full bg-[var(--bg-tertiary)] px-2.5 py-0.5 text-[11px] font-medium text-[var(--text-secondary)]">${label}</span>`;
      },
    },
    {
      headerName: this.translate.instant('pricing.decisions.col.execution_mode'),
      field: 'executionMode',
      width: 100,
      sortable: true,
      cellRenderer: (params: ICellRendererParams<PricingDecisionSummary>) => {
        const mode = params.value as string;
        const label =
          mode === 'SIMULATED'
            ? this.translate.instant('pricing.decisions.execution_mode.SIMULATED')
            : this.translate.instant('pricing.decisions.execution_mode.LIVE');
        if (mode === 'SIMULATED') {
          return `<span class="rounded-full border border-dashed border-[var(--border-default)] px-2 py-0.5 text-[11px] text-[var(--text-secondary)]">${label}</span>`;
        }
        return `<span class="text-[11px] text-[var(--text-primary)]">${label}</span>`;
      },
    },
    {
      headerName: this.translate.instant('pricing.decisions.col.run'),
      field: 'pricingRunId',
      width: 80,
      sortable: true,
      cellClass: 'font-mono',
      cellRenderer: (params: ICellRendererParams<PricingDecisionSummary>) => {
        if (!params.value) return '—';
        return `<span class="text-[var(--accent-primary)] cursor-pointer hover:underline">#${params.value}</span>`;
      },
    },
    {
      headerName: this.translate.instant('pricing.decisions.col.skip_reason'),
      field: 'skipReason',
      minWidth: 200,
      flex: 1,
      sortable: false,
      valueFormatter: (params: ValueFormatterParams<PricingDecisionSummary>) => {
        if (!params.value) return '—';
        return this.translate.instant(params.value);
      },
    },
    {
      headerName: this.translate.instant('pricing.decisions.col.date'),
      field: 'createdAt',
      width: 140,
      sortable: true,
      sort: 'desc' as const,
      valueFormatter: (params: ValueFormatterParams<PricingDecisionSummary>) =>
        this.formatTimestamp(params.value),
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

  readonly getRowId = (params: GetRowIdParams<PricingDecisionSummary>) =>
    String(params.data.id);

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
    this.currentPage.set(0);
  }

  onRowClicked(row: PricingDecisionSummary): void {
    this.detailPanel.open('pricing-decision', row.id);
  }

  private formatPrice(value: number | null): string {
    return formatMoney(value, 0);
  }

  private formatTimestamp(iso: string | null): string {
    return formatDateTime(iso, 'full');
  }
}
