import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import {
  ColDef,
  GetRowIdParams,
  ICellRendererParams,
  ValueFormatterParams,
} from 'ag-grid-community';

import { BiddingApiService } from '@core/api/bidding-api.service';
import { BidDecisionFilter, BidDecisionSummary } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { PaginationBarComponent } from '@shared/components/pagination-bar/pagination-bar.component';
import {
  FilterBarComponent,
  FilterConfig,
} from '@shared/components/filter-bar/filter-bar.component';
import { formatMoney, formatDateTime, renderBadge } from '@shared/utils/format.utils';
import { createListPageState } from '@shared/utils/list-page-state';
import type { FilterBarUrlDef } from '@shared/utils/url-filters';

const DECISION_COLOR: Record<string, string> = {
  BID_UP: 'var(--status-success)',
  BID_DOWN: 'var(--status-error)',
  HOLD: 'var(--status-neutral)',
  PAUSE: 'var(--status-warning)',
  RESUME: 'var(--status-info)',
  SET_MINIMUM: 'var(--status-info)',
  EMERGENCY_CUT: 'var(--status-error)',
};

@Component({
  selector: 'dp-bid-decisions-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    DataGridComponent,
    EmptyStateComponent,
    FilterBarComponent,
    PaginationBarComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <div class="flex h-full flex-col">
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <h2 class="text-sm font-semibold text-[var(--text-primary)]">
          {{ 'bidding.decisions.title' | translate }}
        </h2>
        <button
          (click)="exportCsv()"
          [disabled]="isExporting()"
          class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1 text-[var(--text-xs)] text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] disabled:opacity-50"
        >
          {{ 'bidding.decisions.export_csv' | translate }}
        </button>
      </div>

      <!-- Filter Bar -->
      <div class="border-b border-[var(--border-default)] px-4 py-2">
        <dp-filter-bar
          [filters]="filterConfigs"
          [values]="listState.filterValues()"
          (filtersChanged)="listState.onFiltersChanged($event)"
        />
      </div>

      <!-- Grid -->
      <div class="flex-1 px-4 py-2">
        @if (decisionsQuery.isError()) {
          <dp-empty-state
            [message]="'bidding.decisions.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="decisionsQuery.refetch()"
          />
        } @else if (!decisionsQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="listState.hasActiveFilters()
              ? ('bidding.decisions.empty_filtered' | translate)
              : ('bidding.decisions.empty' | translate)"
            [actionLabel]="listState.hasActiveFilters() ? ('filter_bar.reset_all' | translate) : ''"
            (action)="listState.resetFilters()"
          />
        } @else {
          <dp-data-grid
            viewStateKey="bidding:decisions"
            [columnDefs]="columnDefs"
            [rowData]="rows()"
            [loading]="decisionsQuery.isPending()"
            [pagination]="true"
            [pageSize]="listState.pageSize()"
            [getRowId]="getRowId"
            [height]="'100%'"
            [initialSortModel]="listState.initialSortModel()"
            (sortChanged)="listState.onSortChanged($event)"
          />
          <dp-pagination-bar
            [totalItems]="decisionsQuery.data()?.totalElements ?? 0"
            [pageSize]="listState.pageSize()"
            [currentPage]="listState.currentPage()"
            [pageSizeOptions]="[25, 50, 100]"
            (pageChange)="listState.onPageChanged($event)"
          />
        }
      </div>
    </div>
  `,
})
export class BidDecisionsListPageComponent {
  private readonly biddingApi = inject(BiddingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  readonly isExporting = signal(false);

  private readonly queryOfferId = computed(() => {
    const raw = this.route.snapshot.queryParamMap.get('marketplaceOfferId');
    return raw ? Number(raw) : undefined;
  });

  readonly listState = createListPageState({
    pageKey: 'bidding:decisions',
    defaultSort: { column: 'createdAt', direction: 'desc' },
    defaultPageSize: 50,
    filterBarDefs: [
      { key: 'decisionType', type: 'csv' },
      { key: 'dateFrom', type: 'string' },
      { key: 'dateTo', type: 'string' },
    ] satisfies FilterBarUrlDef[],
  });

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'decisionType',
      label: 'bidding.decisions.filter.decision_type',
      type: 'multi-select',
      options: (['BID_UP', 'BID_DOWN', 'HOLD', 'PAUSE', 'RESUME', 'SET_MINIMUM', 'EMERGENCY_CUT'] as const).map(
        (value) => ({
          value,
          label: `bidding.decision.${value}`,
        }),
      ),
    },
    {
      key: 'dateRange',
      label: 'bidding.decisions.filter.date_from',
      type: 'date-range',
    },
  ];

  private readonly filter = computed<BidDecisionFilter>(() => {
    const vals = this.listState.filterValues();
    const f: BidDecisionFilter = {};
    const offerId = this.queryOfferId();
    if (offerId) f.marketplaceOfferId = offerId;
    if (vals['decisionType']?.length) f.decisionType = vals['decisionType'];
    const range = vals['dateRange'];
    if (range?.from) f.dateFrom = range.from;
    if (range?.to) f.dateTo = range.to;
    return f;
  });

  readonly decisionsQuery = injectQuery(() => ({
    queryKey: [
      'bid-decisions',
      this.wsStore.currentWorkspaceId(),
      this.filter(),
      this.listState.currentPage(),
      this.listState.pageSize(),
      this.listState.sortParam(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.biddingApi.listDecisions(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.listState.currentPage(),
          this.listState.pageSize(),
          this.listState.sortParam(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.decisionsQuery.data()?.content ?? []);

  readonly columnDefs: ColDef[] = [
    {
      headerName: this.translate.instant('bidding.decisions.col.created_at'),
      field: 'createdAt',
      width: 140,
      sortable: true,
      valueFormatter: (params: ValueFormatterParams<BidDecisionSummary>) =>
        formatDateTime(params.value, 'full'),
    },
    {
      headerName: this.translate.instant('bidding.decisions.col.offer'),
      field: 'marketplaceOfferId',
      width: 130,
      sortable: true,
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      cellRenderer: (params: ICellRendererParams<BidDecisionSummary>) => {
        if (!params.value) return '';
        return `<span class="font-medium text-[var(--accent-primary)] cursor-pointer hover:underline">${params.value}</span>`;
      },
      onCellClicked: (params: { data?: BidDecisionSummary }) => {
        if (params.data) this.navigateToDecision(params.data.id);
      },
    },
    {
      headerName: this.translate.instant('bidding.decisions.col.strategy'),
      field: 'strategyType',
      width: 160,
      sortable: true,
      cellRenderer: (params: ICellRendererParams<BidDecisionSummary>) => {
        const st = params.value as string;
        if (!st) return '';
        const label = this.translate.instant(`bidding.policies.strategy.${st}`);
        return renderBadge(label, 'var(--status-info)');
      },
    },
    {
      headerName: this.translate.instant('bidding.decisions.col.decision_type'),
      field: 'decisionType',
      width: 160,
      sortable: true,
      cellRenderer: (params: ICellRendererParams<BidDecisionSummary>) => {
        const dt = params.value as string;
        if (!dt) return '';
        const label = this.translate.instant(`bidding.decisions.type.${dt}`);
        const color = DECISION_COLOR[dt] ?? 'var(--status-neutral)';
        return renderBadge(label, color);
      },
    },
    {
      headerName: this.translate.instant('bidding.decisions.col.current_bid'),
      field: 'currentBid',
      width: 130,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: (params: ValueFormatterParams<BidDecisionSummary>) =>
        this.formatBid(params.value),
    },
    {
      headerName: this.translate.instant('bidding.decisions.col.target_bid'),
      field: 'targetBid',
      width: 130,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: (params: ValueFormatterParams<BidDecisionSummary>) =>
        this.formatBid(params.value),
    },
    {
      headerName: this.translate.instant('bidding.decisions.col.explanation'),
      field: 'explanationSummary',
      minWidth: 200,
      flex: 1,
      sortable: false,
      tooltipField: 'explanationSummary',
      cellClass: 'text-[length:var(--text-sm)] text-[color:var(--text-tertiary)]',
      valueFormatter: (params: ValueFormatterParams<BidDecisionSummary>) => {
        if (!params.value) return '—';
        return params.value.length > 80 ? params.value.substring(0, 80) + '…' : params.value;
      },
    },
  ];

  readonly getRowId = (params: GetRowIdParams<BidDecisionSummary>) =>
    String(params.data.id);

  navigateToDecision(decisionId: number): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'bidding', 'decisions', decisionId]);
  }

  exportCsv(): void {
    this.isExporting.set(true);
    const wsId = this.wsStore.currentWorkspaceId()!;
    this.biddingApi.exportDecisionsCsv(wsId, this.filter()).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `bid-decisions-${new Date().toISOString().slice(0, 10)}.csv`;
        a.click();
        URL.revokeObjectURL(url);
        this.isExporting.set(false);
      },
      error: () => {
        this.toast.error(this.translate.instant('bidding.decisions.export_error'));
        this.isExporting.set(false);
      },
    });
  }

  private formatBid(value: number | null): string {
    if (value === null || value === undefined) return '—';
    return formatMoney(value / 100, 0);
  }
}
