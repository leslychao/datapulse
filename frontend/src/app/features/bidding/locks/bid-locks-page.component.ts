import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
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
import { CreateManualBidLockRequest, ManualBidLock } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { PaginationBarComponent } from '@shared/components/pagination-bar/pagination-bar.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { formatMoney, formatDateTime } from '@shared/utils/format.utils';
import { createListPageState } from '@shared/utils/list-page-state';

@Component({
  selector: 'dp-bid-locks-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    DataGridComponent,
    EmptyStateComponent,
    PaginationBarComponent,
    ConfirmationModalComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <div class="flex h-full flex-col">
      <!-- Toolbar -->
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <h2 class="text-base font-semibold text-[var(--text-primary)]">
          {{ 'bidding.locks.title' | translate }}
        </h2>
        @if (rbac.canManageLocks()) {
          <button
            (click)="showCreateForm.set(true)"
            class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
          >
            {{ 'bidding.locks.create' | translate }}
          </button>
        }
      </div>

      <!-- Inline create panel -->
      @if (showCreateForm()) {
        <div class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-4">
          <div class="flex flex-wrap items-end gap-4">
            <div class="flex flex-col gap-1">
              <label class="text-[11px] text-[var(--text-tertiary)]">
                {{ 'bidding.actions.col.offer' | translate }}
              </label>
              <input
                type="number"
                [(ngModel)]="formOfferId"
                class="h-8 w-36 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                placeholder="12345"
              />
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-[11px] text-[var(--text-tertiary)]">
                {{ 'bidding.locks.col.locked_bid' | translate }}
              </label>
              <input
                type="number"
                [(ngModel)]="formBid"
                class="h-8 w-32 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                placeholder="1000"
              />
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-[11px] text-[var(--text-tertiary)]">
                {{ 'bidding.locks.col.reason' | translate }}
              </label>
              <input
                type="text"
                [(ngModel)]="formReason"
                class="h-8 w-56 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
              />
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-[11px] text-[var(--text-tertiary)]">
                {{ 'bidding.locks.col.expires_at' | translate }}
              </label>
              <input
                type="date"
                [(ngModel)]="formExpiresAt"
                [min]="today"
                class="h-8 w-40 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
              />
            </div>
            <div class="flex gap-2">
              <button
                (click)="submitCreate()"
                [disabled]="!isFormValid()"
                class="h-8 cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
              >
                {{ 'bidding.locks.create' | translate }}
              </button>
              <button
                (click)="cancelCreate()"
                class="h-8 cursor-pointer rounded-[var(--radius-md)] px-3 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
              >
                {{ 'actions.cancel' | translate }}
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Grid -->
      <div class="flex-1 px-4 py-2">
        @if (locksQuery.isError()) {
          <dp-empty-state
            [message]="'bidding.locks.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="locksQuery.refetch()"
          />
        } @else if (!locksQuery.isPending() && rows().length === 0) {
          <dp-empty-state [message]="'bidding.locks.empty' | translate" />
        } @else {
          <dp-data-grid
            viewStateKey="bidding:locks"
            [columnDefs]="columnDefs()"
            [rowData]="rows()"
            [loading]="locksQuery.isPending()"
            [pagination]="true"
            [pageSize]="listState.pageSize()"
            [getRowId]="getRowId"
            [height]="'100%'"
            [initialSortModel]="listState.initialSortModel()"
            (sortChanged)="listState.onSortChanged($event)"
          />
          <dp-pagination-bar
            [totalItems]="locksQuery.data()?.totalElements ?? 0"
            [pageSize]="listState.pageSize()"
            [currentPage]="listState.currentPage()"
            [pageSizeOptions]="[25, 50, 100]"
            (pageChange)="listState.onPageChanged($event)"
          />
        }
      </div>
    </div>

    <dp-confirmation-modal
      [open]="showUnlockModal()"
      [title]="'bidding.locks.unlock_title' | translate"
      [message]="unlockMessage()"
      [confirmLabel]="'bidding.locks.unlock_confirm' | translate"
      (confirmed)="executeUnlock()"
      (cancelled)="showUnlockModal.set(false)"
    />
  `,
})
export class BidLocksPageComponent {
  private readonly biddingApi = inject(BiddingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  private readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  readonly listState = createListPageState({
    pageKey: 'bidding:locks',
    defaultSort: { column: 'createdAt', direction: 'desc' },
    defaultPageSize: 50,
  });

  readonly showCreateForm = signal(false);
  readonly showUnlockModal = signal(false);
  readonly unlockTarget = signal<ManualBidLock | null>(null);

  formOfferId: number | null = null;
  formBid: number | null = null;
  formReason = '';
  formExpiresAt = '';
  protected readonly today = new Date().toISOString().slice(0, 10);

  private readonly baseColumnDefs: ColDef[] = [
    {
      headerName: this.translate.instant('bidding.actions.col.offer'),
      field: 'marketplaceOfferId',
      width: 130,
      sortable: true,
      cellClass: 'font-mono',
    },
    {
      headerName: this.translate.instant('bidding.locks.col.locked_bid'),
      field: 'lockedBid',
      width: 120,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: (params: ValueFormatterParams<ManualBidLock>) =>
        this.formatBid(params.value),
    },
    {
      headerName: this.translate.instant('bidding.locks.col.reason'),
      field: 'reason',
      minWidth: 200,
      flex: 1,
      sortable: false,
      tooltipField: 'reason',
      valueFormatter: (params: ValueFormatterParams<ManualBidLock>) =>
        params.value ?? '—',
    },
    {
      headerName: this.translate.instant('bidding.locks.col.locked_by'),
      field: 'lockedBy',
      width: 100,
      sortable: true,
      cellClass: 'font-mono',
    },
    {
      headerName: this.translate.instant('bidding.locks.col.expires_at'),
      field: 'expiresAt',
      width: 140,
      sortable: true,
      valueFormatter: (params: ValueFormatterParams<ManualBidLock>) =>
        params.value ? formatDateTime(params.value, 'full') : '—',
    },
    {
      headerName: this.translate.instant('bidding.locks.col.created_at'),
      field: 'createdAt',
      width: 140,
      sortable: true,
      valueFormatter: (params: ValueFormatterParams<ManualBidLock>) =>
        formatDateTime(params.value, 'full'),
    },
  ];

  private readonly unlockColumnDef: ColDef = {
    headerName: '',
    field: 'actions',
    width: 60,
    sortable: false,
    suppressMovable: true,
    cellRenderer: () => {
      return `<button class="action-btn text-[var(--status-error)]" data-action="unlock" title="${this.translate.instant('bidding.locks.unlock_confirm')}">✕</button>`;
    },
    onCellClicked: (event: CellClickedEvent<ManualBidLock>) => {
      const target = event.event?.target as HTMLElement;
      if (target?.closest('[data-action="unlock"]') && event.data) {
        this.unlockTarget.set(event.data);
        this.showUnlockModal.set(true);
      }
    },
  };

  readonly columnDefs = computed(() =>
    this.rbac.canManageLocks()
      ? [...this.baseColumnDefs, this.unlockColumnDef]
      : this.baseColumnDefs,
  );

  readonly locksQuery = injectQuery(() => ({
    queryKey: [
      'bid-locks',
      this.wsStore.currentWorkspaceId(),
      this.listState.currentPage(),
      this.listState.pageSize(),
      this.listState.sortParam(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.biddingApi.listLocks(
          this.wsStore.currentWorkspaceId()!,
          this.listState.currentPage(),
          this.listState.pageSize(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.locksQuery.data()?.content ?? []);

  readonly unlockMessage = computed(() => {
    const lock = this.unlockTarget();
    return lock
      ? this.translate.instant('bidding.locks.unlock_message', {
          offerId: lock.marketplaceOfferId,
        })
      : '';
  });

  private readonly createMutation = injectMutation(() => ({
    mutationFn: (req: CreateManualBidLockRequest) =>
      lastValueFrom(
        this.biddingApi.createLock(this.wsStore.currentWorkspaceId()!, req),
      ),
    onSuccess: () => {
      this.resetForm();
      this.queryClient.invalidateQueries({ queryKey: ['bid-locks'] });
      this.toast.success(this.translate.instant('bidding.locks.created'));
    },
    onError: () => this.toast.error(this.translate.instant('bidding.locks.create_error')),
  }));

  private readonly deleteMutation = injectMutation(() => ({
    mutationFn: (lockId: number) =>
      lastValueFrom(
        this.biddingApi.deleteLock(this.wsStore.currentWorkspaceId()!, lockId),
      ),
    onSuccess: () => {
      this.showUnlockModal.set(false);
      this.unlockTarget.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['bid-locks'] });
      this.toast.success(this.translate.instant('bidding.locks.deleted'));
    },
    onError: () => {
      this.showUnlockModal.set(false);
      this.toast.error(this.translate.instant('bidding.locks.delete_error'));
    },
  }));

  readonly getRowId = (params: GetRowIdParams<ManualBidLock>) =>
    String(params.data.id);

  isFormValid(): boolean {
    return this.formOfferId !== null && this.formOfferId > 0;
  }

  submitCreate(): void {
    if (!this.isFormValid()) return;
    const req: CreateManualBidLockRequest = {
      marketplaceOfferId: this.formOfferId!,
    };
    if (this.formBid !== null) req.lockedBid = this.formBid;
    if (this.formReason.trim()) req.reason = this.formReason.trim();
    if (this.formExpiresAt) req.expiresAt = this.formExpiresAt;
    this.createMutation.mutate(req);
  }

  cancelCreate(): void {
    this.resetForm();
  }

  executeUnlock(): void {
    const target = this.unlockTarget();
    if (target) this.deleteMutation.mutate(target.id);
  }

  private resetForm(): void {
    this.showCreateForm.set(false);
    this.formOfferId = null;
    this.formBid = null;
    this.formReason = '';
    this.formExpiresAt = '';
  }

  private formatBid(value: number | null): string {
    if (value === null || value === undefined) return '—';
    return formatMoney(value / 100, 0);
  }
}
