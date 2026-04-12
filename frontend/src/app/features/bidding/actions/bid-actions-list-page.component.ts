import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import {
  CellClickedEvent,
  ColDef,
  GetRowIdParams,
  ICellRendererParams,
  ValueFormatterParams,
} from 'ag-grid-community';

import { BiddingApiService } from '@core/api/bidding-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { BidActionSummary } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { PaginationBarComponent } from '@shared/components/pagination-bar/pagination-bar.component';
import {
  FilterBarComponent,
  FilterConfig,
} from '@shared/components/filter-bar/filter-bar.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { formatMoney, formatDateTime, renderBadge } from '@shared/utils/format.utils';
import { platformColumn } from '@shared/utils/column-factories';
import { createListPageState } from '@shared/utils/list-page-state';
import type { FilterBarUrlDef } from '@shared/utils/url-filters';

const STATUS_COLOR: Record<string, string> = {
  PENDING_APPROVAL: 'var(--status-info)',
  APPROVED: 'var(--status-success)',
  SCHEDULED: 'var(--status-info)',
  EXECUTING: 'var(--status-info)',
  SUCCEEDED: 'var(--status-success)',
  FAILED: 'var(--status-error)',
  RETRY_SCHEDULED: 'var(--status-warning)',
  ON_HOLD: 'var(--status-neutral)',
  SUPERSEDED: 'var(--status-neutral)',
  CANCELLED: 'var(--status-neutral)',
  EXPIRED: 'var(--status-neutral)',
};

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
  selector: 'dp-bid-actions-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    DataGridComponent,
    EmptyStateComponent,
    FilterBarComponent,
    PaginationBarComponent,
    ConfirmationModalComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <div class="flex h-full flex-col">
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <h2 class="text-sm font-semibold text-[var(--text-primary)]">
          {{ 'bidding.actions.title' | translate }}
        </h2>
        @if (rbac.canApproveBidActions() && pendingCount() > 0) {
          <div class="flex items-center gap-2">
            <button
              (click)="showBulkApproveModal.set(true)"
              class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-3 py-1 text-[var(--text-xs)] font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
            >
              {{ 'bidding.actions.bulk_approve' | translate }}
            </button>
            <button
              (click)="showBulkRejectModal.set(true)"
              class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1 text-[var(--text-xs)] text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              {{ 'bidding.actions.bulk_reject' | translate }}
            </button>
          </div>
        }
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
        @if (actionsQuery.isError()) {
          <dp-empty-state
            [message]="'bidding.actions.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="actionsQuery.refetch()"
          />
        } @else if (!actionsQuery.isPending() && rows().length === 0) {
          <dp-empty-state [message]="'bidding.actions.empty' | translate" />
        } @else {
          <dp-data-grid
            viewStateKey="bidding:actions"
            [columnDefs]="columnDefs()"
            [rowData]="rows()"
            [loading]="actionsQuery.isPending()"
            [pagination]="true"
            [pageSize]="listState.pageSize()"
            [getRowId]="getRowId"
            [height]="'100%'"
            [initialSortModel]="listState.initialSortModel()"
            (sortChanged)="listState.onSortChanged($event)"
          />
          <dp-pagination-bar
            [totalItems]="actionsQuery.data()?.totalElements ?? 0"
            [pageSize]="listState.pageSize()"
            [currentPage]="listState.currentPage()"
            [pageSizeOptions]="[25, 50, 100]"
            (pageChange)="listState.onPageChanged($event)"
          />
        }
      </div>
    </div>

    <dp-confirmation-modal
      [open]="showBulkApproveModal()"
      [title]="'bidding.actions.bulk_approve' | translate"
      [message]="bulkApproveMessage()"
      [confirmLabel]="'bidding.actions.approve' | translate"
      (confirmed)="bulkApprove()"
      (cancelled)="showBulkApproveModal.set(false)"
    />

    <dp-confirmation-modal
      [open]="showBulkRejectModal()"
      [title]="'bidding.actions.bulk_reject' | translate"
      [message]="bulkRejectMessage()"
      [confirmLabel]="'bidding.actions.reject' | translate"
      [danger]="true"
      (confirmed)="bulkReject()"
      (cancelled)="showBulkRejectModal.set(false)"
    />
  `,
})
export class BidActionsListPageComponent {
  private readonly biddingApi = inject(BiddingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  private readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  readonly showBulkApproveModal = signal(false);
  readonly showBulkRejectModal = signal(false);

  readonly listState = createListPageState({
    pageKey: 'bidding:actions',
    defaultSort: { column: 'createdAt', direction: 'desc' },
    defaultPageSize: 50,
    filterBarDefs: [
      { key: 'status', type: 'csv' },
      { key: 'executionMode', type: 'csv' },
    ] satisfies FilterBarUrlDef[],
  });

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: 'bidding.actions.col.status',
      type: 'multi-select',
      options: (['PENDING_APPROVAL', 'APPROVED', 'SUCCEEDED', 'FAILED', 'RETRY_SCHEDULED', 'ON_HOLD', 'SUPERSEDED', 'CANCELLED'] as const).map(
        (value) => ({
          value,
          label: `bidding.actions.status.${value}`,
        }),
      ),
    },
    {
      key: 'executionMode',
      label: 'bidding.policies.filter.execution_mode',
      type: 'multi-select',
      options: (['RECOMMENDATION', 'SEMI_AUTO', 'FULL_AUTO'] as const).map(
        (value) => ({
          value,
          label: `bidding.policies.mode.${value}`,
        }),
      ),
    },
  ];

  readonly columnDefs = computed(() => {
    const cols: ColDef[] = [
      {
        headerName: 'ID',
        field: 'id',
        width: 80,
        sortable: true,
        cellClass: 'font-mono',
      },
      {
        headerName: this.translate.instant('bidding.actions.col.offer'),
        field: 'marketplaceOfferId',
        width: 120,
        sortable: true,
        cellClass: 'font-mono',
      },
      { ...platformColumn(this.translate, 'marketplaceType', 'bidding.actions.col.marketplace', 100), sortable: true },
      {
        headerName: this.translate.instant('bidding.actions.col.decision_type'),
        field: 'decisionType',
        width: 150,
        sortable: true,
        cellRenderer: (params: ICellRendererParams<BidActionSummary>) => {
          const dt = params.value as string;
          if (!dt) return '';
          const label = this.translate.instant(`bidding.decisions.type.${dt}`);
          const color = DECISION_COLOR[dt] ?? 'var(--status-neutral)';
          return renderBadge(label, color);
        },
      },
      {
        headerName: this.translate.instant('bidding.actions.col.previous_bid'),
        field: 'previousBid',
        width: 110,
        sortable: true,
        type: 'rightAligned',
        cellClass: 'font-mono text-[length:var(--text-sm)]',
        valueFormatter: (params: ValueFormatterParams<BidActionSummary>) =>
          this.formatBid(params.value),
      },
      {
        headerName: this.translate.instant('bidding.actions.col.target_bid'),
        field: 'targetBid',
        width: 110,
        sortable: true,
        type: 'rightAligned',
        cellClass: 'font-mono text-[length:var(--text-sm)]',
        valueFormatter: (params: ValueFormatterParams<BidActionSummary>) =>
          this.formatBid(params.value),
      },
      {
        headerName: this.translate.instant('bidding.actions.col.status'),
        field: 'status',
        width: 160,
        sortable: true,
        cellRenderer: (params: ICellRendererParams<BidActionSummary>) => {
          const st = params.value as string;
          if (!st) return '';
          const label = this.translate.instant(`bidding.actions.status.${st}`);
          const color = STATUS_COLOR[st] ?? 'var(--status-neutral)';
          return renderBadge(label, color);
        },
      },
      {
        headerName: this.translate.instant('bidding.actions.col.mode'),
        field: 'executionMode',
        width: 130,
        sortable: true,
        valueFormatter: (params: ValueFormatterParams<BidActionSummary>) =>
          this.translate.instant('bidding.policies.mode.' + params.value),
      },
      {
        headerName: this.translate.instant('bidding.actions.col.created_at'),
        field: 'createdAt',
        width: 140,
        sortable: true,
        valueFormatter: (params: ValueFormatterParams<BidActionSummary>) =>
          formatDateTime(params.value, 'full'),
      },
    ];

    if (this.rbac.canApproveBidActions()) {
      cols.push({
        headerName: '',
        field: '_approve',
        width: 160,
        sortable: false,
        suppressMovable: true,
        cellRenderer: (params: ICellRendererParams<BidActionSummary>) => {
          if (!params.data || params.data.status !== 'PENDING_APPROVAL') return '';
          return `<div class="flex items-center gap-1">
            <button class="action-btn text-[var(--status-success)]" data-action="approve" title="${this.translate.instant('bidding.actions.approve')}">✓</button>
            <button class="action-btn text-[var(--status-error)]" data-action="reject" title="${this.translate.instant('bidding.actions.reject')}">✕</button>
          </div>`;
        },
        onCellClicked: (event: CellClickedEvent<BidActionSummary>) => {
          const target = event.event?.target as HTMLElement;
          const action = target?.closest('[data-action]')?.getAttribute('data-action');
          if (!action || !event.data) return;
          if (action === 'approve') this.approveOne(event.data.id);
          if (action === 'reject') this.rejectOne(event.data.id);
        },
      });
    }

    return cols;
  });

  readonly actionsQuery = injectQuery(() => ({
    queryKey: [
      'bid-actions',
      this.wsStore.currentWorkspaceId(),
      this.listState.currentPage(),
      this.listState.pageSize(),
      this.listState.sortParam(),
      this.listState.filterValues(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.biddingApi.listPendingActions(
          this.wsStore.currentWorkspaceId()!,
          this.listState.currentPage(),
          this.listState.pageSize(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 15_000,
  }));

  readonly rows = computed(() => this.actionsQuery.data()?.content ?? []);

  readonly pendingCount = computed(() =>
    this.rows().filter((r) => r.status === 'PENDING_APPROVAL').length,
  );

  private readonly approveMutation = injectMutation(() => ({
    mutationFn: (actionId: number) =>
      lastValueFrom(
        this.biddingApi.approveAction(this.wsStore.currentWorkspaceId()!, actionId),
      ),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['bid-actions'] });
      this.toast.success(this.translate.instant('bidding.actions.approved'));
    },
    onError: () =>
      this.toast.error(this.translate.instant('bidding.actions.approve_error')),
  }));

  private readonly rejectMutation = injectMutation(() => ({
    mutationFn: (actionId: number) =>
      lastValueFrom(
        this.biddingApi.rejectAction(this.wsStore.currentWorkspaceId()!, actionId),
      ),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['bid-actions'] });
      this.toast.success(this.translate.instant('bidding.actions.rejected'));
    },
    onError: () =>
      this.toast.error(this.translate.instant('bidding.actions.reject_error')),
  }));

  private readonly bulkApproveMutation = injectMutation(() => ({
    mutationFn: (ids: number[]) =>
      lastValueFrom(
        this.biddingApi.bulkApproveActions(this.wsStore.currentWorkspaceId()!, ids),
      ),
    onSuccess: () => {
      this.showBulkApproveModal.set(false);
      this.queryClient.invalidateQueries({ queryKey: ['bid-actions'] });
      this.toast.success(this.translate.instant('bidding.actions.approved'));
    },
    onError: () => {
      this.showBulkApproveModal.set(false);
      this.toast.error(this.translate.instant('bidding.actions.approve_error'));
    },
  }));

  private readonly bulkRejectMutation = injectMutation(() => ({
    mutationFn: (ids: number[]) =>
      lastValueFrom(
        this.biddingApi.bulkRejectActions(this.wsStore.currentWorkspaceId()!, ids),
      ),
    onSuccess: () => {
      this.showBulkRejectModal.set(false);
      this.queryClient.invalidateQueries({ queryKey: ['bid-actions'] });
      this.toast.success(this.translate.instant('bidding.actions.rejected'));
    },
    onError: () => {
      this.showBulkRejectModal.set(false);
      this.toast.error(this.translate.instant('bidding.actions.reject_error'));
    },
  }));

  readonly getRowId = (params: GetRowIdParams<BidActionSummary>) =>
    String(params.data.id);

  bulkApproveMessage(): string {
    return this.translate.instant('bidding.actions.bulk_approve_message', { count: this.pendingCount() });
  }

  bulkRejectMessage(): string {
    return this.translate.instant('bidding.actions.bulk_reject_message', { count: this.pendingCount() });
  }

  approveOne(actionId: number): void {
    this.approveMutation.mutate(actionId);
  }

  rejectOne(actionId: number): void {
    this.rejectMutation.mutate(actionId);
  }

  bulkApprove(): void {
    const ids = this.rows()
      .filter((r) => r.status === 'PENDING_APPROVAL')
      .map((r) => r.id);
    if (ids.length) this.bulkApproveMutation.mutate(ids);
  }

  bulkReject(): void {
    const ids = this.rows()
      .filter((r) => r.status === 'PENDING_APPROVAL')
      .map((r) => r.id);
    if (ids.length) this.bulkRejectMutation.mutate(ids);
  }

  private formatBid(value: number | null): string {
    if (value === null || value === undefined) return '—';
    return formatMoney(value / 100, 0);
  }
}
