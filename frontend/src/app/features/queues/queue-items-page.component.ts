import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  OnDestroy,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AgGridAngular } from 'ag-grid-angular';
import {
  CellClickedEvent,
  ColDef,
  GridApi,
  GridReadyEvent,
  ICellRendererParams,
  SelectionChangedEvent,
} from 'ag-grid-community';
import {
  injectMutation,
  injectQuery,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { LucideAngularModule, CheckCircle2, Columns3, Download, AlignJustify, AlignCenter } from 'lucide-angular';
import { lastValueFrom, Subject, Subscription } from 'rxjs';
import { debounceTime, takeUntil } from 'rxjs/operators';

import { ActionApiService } from '@core/api/action-api.service';
import { QueueApiService } from '@core/api/queue-api.service';
import {
  Queue,
  QueueFilter,
  QueueItem,
  QueueItemStatus,
  SystemQueueCode,
} from '@core/models';
import { RbacService } from '@core/auth/rbac.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { QueueStore } from '@shared/stores/queue.store';
import { DetailPanelService } from '@shared/services/detail-panel.service';
import { ShortcutService } from '@shared/services/shortcut.service';
import { ToastService } from '@shared/shell/toast/toast.service';
import { WebSocketService } from '@core/websocket/websocket.service';
import {
  formatMoney,
  formatPercent,
  formatDateTime,
  formatInteger,
  financeColor,
  renderBadge,
} from '@shared/utils/format.utils';

type QueueContext = SystemQueueCode | 'CUSTOM';

const QUEUE_DESCRIPTIONS: Record<SystemQueueCode, string> = {
  PENDING_APPROVAL: 'queues.desc.pending_approval',
  EXECUTION_ERRORS: 'queues.desc.execution_errors',
  MISMATCHES: 'queues.desc.mismatches',
  NO_COST: 'queues.desc.no_cost',
  CRITICAL_STOCK: 'queues.desc.critical_stock',
  RECENT_DECISIONS: 'queues.desc.recent_decisions',
};

const DEFAULT_SORT: Record<SystemQueueCode, { field: string; dir: 'ASC' | 'DESC' }> = {
  PENDING_APPROVAL: { field: 'created_at', dir: 'ASC' },
  EXECUTION_ERRORS: { field: 'updated_at', dir: 'DESC' },
  MISMATCHES: { field: 'severity', dir: 'DESC' },
  NO_COST: { field: 'revenue_30d', dir: 'DESC' },
  CRITICAL_STOCK: { field: 'days_of_cover', dir: 'ASC' },
  RECENT_DECISIONS: { field: 'created_at', dir: 'DESC' },
};

const DOT_COLORS: Record<string, string> = {
  ATTENTION: 'bg-[var(--status-error)]',
  DECISION: 'bg-[var(--status-warning)]',
  PROCESSING: 'bg-[var(--status-info)]',
};

@Component({
  selector: 'dp-queue-items-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, AgGridAngular, TranslatePipe, FormsModule, LucideAngularModule],
  templateUrl: './queue-items-page.component.html',
})
export class QueueItemsPageComponent implements OnInit, OnDestroy {
  private readonly queueApi = inject(QueueApiService);
  private readonly actionApi = inject(ActionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly queueStore = inject(QueueStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);
  private readonly detailPanel = inject(DetailPanelService);
  private readonly shortcuts = inject(ShortcutService);
  private readonly ws = inject(WebSocketService);
  protected readonly rbac = inject(RbacService);

  readonly queueId = input.required<string>();
  readonly checkIcon = CheckCircle2;
  readonly columnsIcon = Columns3;
  readonly downloadIcon = Download;
  readonly densityCompactIcon = AlignJustify;
  readonly densityComfortIcon = AlignCenter;

  readonly compactMode = signal(true);

  readonly statusOptions: QueueItemStatus[] = ['PENDING', 'IN_PROGRESS', 'DONE', 'DISMISSED'];
  readonly pageSizeOptions = [20, 50, 100] as const;
  readonly severityOptions = ['WARNING', 'CRITICAL'] as const;
  readonly mismatchTypeOptions = ['PRICE', 'STOCK', 'PROMO', 'FINANCE'] as const;
  readonly decisionTypeOptions = ['CHANGE', 'SKIP', 'HOLD'] as const;
  readonly actionStatusOptions = ['SUCCEEDED', 'FAILED', 'PENDING_APPROVAL', 'ON_HOLD', 'CANCELLED'] as const;

  readonly searchInput$ = new Subject<string>();
  private readonly destroy$ = new Subject<void>();
  readonly searchRef = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly pageIndex = this.queueStore.pageIndex;
  readonly pageSize = this.queueStore.pageSize;
  readonly statusFilter = signal<QueueItemStatus | ''>('');
  readonly marketplaceFilter = signal<string[]>([]);
  readonly assignedToMe = signal(false);
  readonly searchQuery = signal('');
  readonly severityFilter = signal<string[]>([]);
  readonly mismatchTypeFilter = signal<string[]>([]);
  readonly decisionTypeFilter = signal<string[]>([]);
  readonly actionStatusFilter = signal<string[]>([]);
  readonly pageInputValue = signal('');

  readonly selectedRows = signal<QueueItem[]>([]);
  readonly bulkApproveOpen = signal(false);
  readonly bulkRejectOpen = signal(false);
  readonly bulkRejectReason = signal('');
  readonly inlineRejectItemId = signal<number | null>(null);
  readonly inlineRejectReason = signal('');
  readonly pendingActionIds = signal<Set<number>>(new Set());
  readonly inlineHoldItemId = signal<number | null>(null);
  readonly inlineHoldReason = signal('');
  readonly inlineCostItemId = signal<number | null>(null);
  readonly inlineCostValue = signal('');

  readonly contextMenu = signal<{
    x: number;
    y: number;
    item: QueueItem;
  } | null>(null);

  readonly queueIdNum = computed(() => Number(this.queueId()));
  readonly workspaceId = computed(() => this.wsStore.currentWorkspaceId());

  readonly defaultColDef: ColDef<QueueItem> = {
    flex: 1,
    minWidth: 80,
    sortable: true,
    resizable: true,
  };

  readonly queueQuery = injectQuery(() => {
    const ws = this.workspaceId();
    const qid = this.queueIdNum();
    return {
      queryKey: ['queue', ws, qid] as const,
      queryFn: () => lastValueFrom(this.queueApi.getQueue(ws!, qid)),
      enabled: ws != null && ws > 0 && Number.isFinite(qid) && qid > 0,
    };
  });

  readonly queueContext = computed<QueueContext>(() => {
    const q = this.queueQuery.data();
    return q?.systemCode ?? 'CUSTOM';
  });

  readonly queueDescription = computed(() => {
    const ctx = this.queueContext();
    if (ctx === 'CUSTOM') return '';
    const key = QUEUE_DESCRIPTIONS[ctx];
    return key ? this.translate.instant(key) : '';
  });

  readonly sortConfig = computed(() => {
    const ctx = this.queueContext();
    if (ctx === 'CUSTOM') return { field: 'created_at', dir: 'DESC' as const };
    return DEFAULT_SORT[ctx];
  });

  readonly itemsQuery = injectQuery(() => {
    const ws = this.workspaceId();
    const qid = this.queueIdNum();
    const page = this.pageIndex();
    const size = this.pageSize();
    const sort = this.sortConfig();
    const filter = this.buildFilter();
    return {
      queryKey: ['queueItems', ws, qid, page, size, filter, sort] as const,
      queryFn: () =>
        lastValueFrom(
          this.queueApi.listItems(ws!, qid, filter, page, size, sort.field, sort.dir),
        ),
      enabled: ws != null && ws > 0 && Number.isFinite(qid) && qid > 0,
    };
  });

  // --- Mutations ---

  readonly approveMutation = injectMutation(() => ({
    mutationFn: (actionId: number) => {
      const ws = this.workspaceId()!;
      return lastValueFrom(this.actionApi.approveAction(ws, actionId));
    },
    onSuccess: (_data: void, actionId: number) => {
      this.clearPending(actionId);
      this.afterMutation();
      this.toast.success(this.translate.instant('queues.toast.approved'));
    },
    onError: (err: unknown, actionId: number) => {
      this.clearPending(actionId);
      this.handleActionError(err);
    },
  }));

  readonly rejectMutation = injectMutation(() => ({
    mutationFn: (payload: { actionId: number; reason: string }) => {
      const ws = this.workspaceId()!;
      return lastValueFrom(this.actionApi.rejectAction(ws, payload.actionId, payload.reason));
    },
    onSuccess: () => {
      this.afterMutation();
      this.toast.success(this.translate.instant('queues.toast.rejected'));
    },
    onError: (err: unknown) => this.handleActionError(err),
  }));

  readonly holdMutation = injectMutation(() => ({
    mutationFn: (payload: { actionId: number; reason: string }) => {
      const ws = this.workspaceId()!;
      return lastValueFrom(this.actionApi.holdAction(ws, payload.actionId, payload.reason));
    },
    onSuccess: () => {
      this.afterMutation();
      this.toast.success(this.translate.instant('queues.toast.held'));
    },
    onError: (err: unknown) => this.handleActionError(err),
  }));

  readonly retryMutation = injectMutation(() => ({
    mutationFn: (actionId: number) => {
      const ws = this.workspaceId()!;
      return lastValueFrom(this.actionApi.retryAction(ws, actionId, ''));
    },
    onSuccess: () => {
      this.afterMutation();
      this.toast.success(this.translate.instant('queues.toast.retried'));
    },
    onError: (err: unknown) => this.handleActionError(err),
  }));

  readonly cancelMutation = injectMutation(() => ({
    mutationFn: (payload: { actionId: number; reason: string }) => {
      const ws = this.workspaceId()!;
      return lastValueFrom(this.actionApi.cancelAction(ws, payload.actionId, payload.reason));
    },
    onSuccess: () => {
      this.afterMutation();
      this.toast.success(this.translate.instant('queues.toast.cancelled'));
    },
    onError: (err: unknown) => this.handleActionError(err),
  }));

  readonly claimMutation = injectMutation(() => ({
    mutationFn: (itemId: number) => {
      const ws = this.workspaceId()!;
      const qid = this.queueIdNum();
      return lastValueFrom(this.queueApi.claimItem(ws, qid, itemId));
    },
    onSuccess: () => this.afterMutation(),
    onError: (err: unknown) => this.handleActionError(err),
  }));

  readonly completeMutation = injectMutation(() => ({
    mutationFn: (itemId: number) => {
      const ws = this.workspaceId()!;
      const qid = this.queueIdNum();
      return lastValueFrom(this.queueApi.completeItem(ws, qid, itemId));
    },
    onSuccess: () => this.afterMutation(),
    onError: (err: unknown) => this.handleActionError(err),
  }));

  readonly dismissMutation = injectMutation(() => ({
    mutationFn: (itemId: number) => {
      const ws = this.workspaceId()!;
      const qid = this.queueIdNum();
      return lastValueFrom(this.queueApi.dismissItem(ws, qid, itemId));
    },
    onSuccess: () => this.afterMutation(),
    onError: (err: unknown) => this.handleActionError(err),
  }));

  readonly bulkApproveMutation = injectMutation(() => ({
    mutationFn: (ids: number[]) => {
      const ws = this.workspaceId()!;
      return lastValueFrom(this.actionApi.bulkApprove(ws, { actionIds: ids }));
    },
    onSuccess: () => {
      this.afterBulk();
      this.toast.success(this.translate.instant('queues.toast.bulk_approved'));
    },
    onError: () => this.toast.error(this.translate.instant('queues.toast.action_error')),
  }));

  readonly bulkRejectMutation = injectMutation(() => ({
    mutationFn: (payload: { ids: number[]; reason: string }) => {
      const ws = this.workspaceId()!;
      return lastValueFrom(
        this.actionApi.bulkReject(ws, { actionIds: payload.ids, cancelReason: payload.reason }),
      );
    },
    onSuccess: () => {
      this.afterBulk();
      this.toast.success(this.translate.instant('queues.toast.bulk_rejected'));
    },
    onError: () => this.toast.error(this.translate.instant('queues.toast.action_error')),
  }));

  readonly bulkRetryMutation = injectMutation(() => ({
    mutationFn: (ids: number[]) => {
      const ws = this.workspaceId()!;
      const tasks = ids.map((id) => lastValueFrom(this.actionApi.retryAction(ws, id, '')));
      return Promise.all(tasks);
    },
    onSuccess: () => {
      this.afterBulk();
      this.toast.success(this.translate.instant('queues.toast.bulk_retried'));
    },
    onError: () => this.toast.error(this.translate.instant('queues.toast.action_error')),
  }));

  readonly bulkCancelMutation = injectMutation(() => ({
    mutationFn: (ids: number[]) => {
      const ws = this.workspaceId()!;
      const tasks = ids.map((id) =>
        lastValueFrom(this.actionApi.cancelAction(ws, id, '')),
      );
      return Promise.all(tasks);
    },
    onSuccess: () => {
      this.afterBulk();
      this.toast.success(this.translate.instant('queues.toast.bulk_cancelled'));
    },
    onError: () => this.toast.error(this.translate.instant('queues.toast.action_error')),
  }));

  readonly bulkAcknowledgeMutation = injectMutation(() => ({
    mutationFn: (itemIds: number[]) => {
      const ws = this.workspaceId()!;
      const qid = this.queueIdNum();
      const tasks = itemIds.map((id) => lastValueFrom(this.queueApi.claimItem(ws, qid, id)));
      return Promise.all(tasks);
    },
    onSuccess: () => {
      this.afterBulk();
      this.toast.success(this.translate.instant('queues.toast.bulk_acknowledged'));
    },
    onError: () => this.toast.error(this.translate.instant('queues.toast.action_error')),
  }));

  // --- Computed ---

  readonly rowSelectionConfig = {
    mode: 'multiRow' as const,
    checkboxes: true,
    headerCheckbox: true,
    enableClickSelection: false,
  };

  readonly columnDefs = computed(() => this.buildColumnDefs(this.queueContext()));
  readonly rowData = computed(() => this.itemsQuery.data()?.content ?? []);
  readonly totalElements = computed(() => this.itemsQuery.data()?.totalElements ?? 0);
  readonly totalPages = computed(() => Math.max(1, this.itemsQuery.data()?.totalPages ?? 1));
  readonly dotColor = computed(() => DOT_COLORS[this.queueQuery.data()?.queueType ?? ''] ?? '');

  readonly selectedPriceActionIds = computed(() =>
    this.selectedRows()
      .filter((r) => r.entityType === 'price_action')
      .map((r) => r.entityId),
  );

  readonly selectedItemIds = computed(() => this.selectedRows().map((r) => r.itemId));

  readonly showApprovalBulk = computed(
    () => this.queueContext() === 'PENDING_APPROVAL' && this.selectedPriceActionIds().length > 0,
  );

  readonly bulkApproveItems = computed(() =>
    this.selectedRows()
      .filter((r) => r.entityType === 'price_action')
      .map((r) => ({
        sku: this.summary(r, 'skuCode') ?? '—',
        current: this.summaryNum(r, 'currentPrice'),
        target: this.summaryNum(r, 'targetPrice'),
        delta: this.summaryNum(r, 'priceChangePct'),
      })),
  );

  readonly showRetryBulk = computed(
    () => this.queueContext() === 'EXECUTION_ERRORS' && this.selectedPriceActionIds().length > 0,
  );

  readonly showAcknowledgeBulk = computed(
    () => this.queueContext() === 'MISMATCHES' && this.selectedRows().length > 0,
  );

  readonly paginationLabel = computed(() => {
    const page = this.pageIndex();
    const size = this.pageSize();
    const total = this.totalElements();
    const from = total === 0 ? 0 : page * size + 1;
    const to = Math.min((page + 1) * size, total);
    return this.translate.instant('queues.page.showing', { from, to, total });
  });

  readonly hasActiveFilters = computed(() =>
    !!this.statusFilter() ||
    this.marketplaceFilter().length > 0 ||
    this.assignedToMe() ||
    !!this.searchQuery() ||
    this.severityFilter().length > 0 ||
    this.mismatchTypeFilter().length > 0 ||
    this.decisionTypeFilter().length > 0 ||
    this.actionStatusFilter().length > 0,
  );

  readonly showSeverityFilter = computed(() => this.queueContext() === 'MISMATCHES');
  readonly showMismatchTypeFilter = computed(() => this.queueContext() === 'MISMATCHES');
  readonly showDecisionTypeFilter = computed(() => this.queueContext() === 'RECENT_DECISIONS');
  readonly showActionStatusFilter = computed(() => {
    const ctx = this.queueContext();
    return ctx === 'EXECUTION_ERRORS' || ctx === 'RECENT_DECISIONS';
  });

  private gridApi: GridApi<QueueItem> | null = null;
  private queueWsSub: Subscription | null = null;

  constructor() {
    effect(() => {
      const id = this.queueIdNum();
      if (Number.isFinite(id) && id > 0) {
        this.queueStore.selectQueue(id);
        this.detailPanel.close();
      }
    });

    effect(() => {
      const wsId = this.workspaceId();
      const qId = this.queueIdNum();
      this.queueWsSub?.unsubscribe();
      this.queueWsSub = null;
      if (wsId && qId > 0) {
        this.queueWsSub = this.ws.subscribeToQueueItems(wsId, qId, (msg) => {
          if (msg.type === 'ITEM_ADDED' || msg.type === 'ITEM_RESOLVED' || msg.type === 'ITEM_UPDATED') {
            this.afterMutation();
          }
        });
      }
    });
  }

  ngOnInit(): void {
    this.searchInput$
      .pipe(debounceTime(300), takeUntil(this.destroy$))
      .subscribe((value) => {
        this.searchQuery.set(value);
        this.queueStore.setPage(0);
      });

    this.registerShortcuts();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.queueWsSub?.unsubscribe();
  }

  // --- Grid events ---

  onGridReady(event: GridReadyEvent<QueueItem>): void {
    this.gridApi = event.api;
  }

  onSelectionChanged(event: SelectionChangedEvent<QueueItem>): void {
    const rows = event.api.getSelectedRows();
    this.selectedRows.set(rows);
    this.queueStore.setSelectedItemIds(new Set(rows.map((r) => r.itemId)));
  }

  onRowClicked(item: QueueItem): void {
    this.detailPanel.open('action', item.entityId);
  }

  getRowId = (params: { data?: QueueItem }): string =>
    params.data ? String(params.data.itemId) : '';

  // --- Filter handlers ---

  onStatusChange(value: string): void {
    this.statusFilter.set(value as QueueItemStatus | '');
    this.queueStore.setPage(0);
  }

  onMarketplaceChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const values = Array.from(select.selectedOptions).map((o) => o.value);
    this.marketplaceFilter.set(values);
    this.queueStore.setPage(0);
  }

  onAssignedToggle(): void {
    this.assignedToMe.update((v) => !v);
    this.queueStore.setPage(0);
  }

  onSearchInput(event: Event): void {
    this.searchInput$.next((event.target as HTMLInputElement).value);
  }

  onSeverityChange(event: Event): void {
    const val = (event.target as HTMLSelectElement).value;
    this.severityFilter.set(val ? [val] : []);
    this.queueStore.setPage(0);
  }

  onMismatchTypeChange(event: Event): void {
    const val = (event.target as HTMLSelectElement).value;
    this.mismatchTypeFilter.set(val ? [val] : []);
    this.queueStore.setPage(0);
  }

  onDecisionTypeChange(event: Event): void {
    const val = (event.target as HTMLSelectElement).value;
    this.decisionTypeFilter.set(val ? [val] : []);
    this.queueStore.setPage(0);
  }

  onActionStatusChange(event: Event): void {
    const val = (event.target as HTMLSelectElement).value;
    this.actionStatusFilter.set(val ? [val] : []);
    this.queueStore.setPage(0);
  }

  resetFilters(): void {
    this.statusFilter.set('');
    this.marketplaceFilter.set([]);
    this.assignedToMe.set(false);
    this.searchQuery.set('');
    this.severityFilter.set([]);
    this.mismatchTypeFilter.set([]);
    this.decisionTypeFilter.set([]);
    this.actionStatusFilter.set([]);
    this.queueStore.resetFilters();
  }

  // --- Pagination ---

  setPageSize(n: number): void {
    this.queueStore.setPageSize(n);
  }

  prevPage(): void {
    this.queueStore.setPage(Math.max(0, this.pageIndex() - 1));
  }

  nextPage(): void {
    const max = this.totalPages() - 1;
    this.queueStore.setPage(Math.min(max, this.pageIndex() + 1));
  }

  goToPage(event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    const num = Number(val);
    if (Number.isFinite(num) && num >= 1 && num <= this.totalPages()) {
      this.queueStore.setPage(num - 1);
    }
    this.pageInputValue.set('');
  }

  // --- Single actions ---

  approveOne(item: QueueItem): void {
    if (item.entityType === 'price_action') {
      this.markPending(item.entityId);
      this.approveMutation.mutate(item.entityId);
    }
  }

  openInlineReject(item: QueueItem): void {
    this.inlineRejectItemId.set(item.entityId);
    this.inlineRejectReason.set('');
  }

  confirmInlineReject(): void {
    const reason = this.inlineRejectReason().trim();
    const id = this.inlineRejectItemId();
    if (!reason || !id) return;
    this.rejectMutation.mutate({ actionId: id, reason });
    this.inlineRejectItemId.set(null);
  }

  cancelInlineReject(): void {
    this.inlineRejectItemId.set(null);
    this.inlineRejectReason.set('');
  }

  holdOne(item: QueueItem): void {
    if (item.entityType === 'price_action') {
      this.inlineHoldItemId.set(item.entityId);
      this.inlineHoldReason.set('');
    }
  }

  confirmInlineHold(): void {
    const id = this.inlineHoldItemId();
    if (id) {
      this.holdMutation.mutate({ actionId: id, reason: this.inlineHoldReason() });
      this.cancelInlineHold();
    }
  }

  cancelInlineHold(): void {
    this.inlineHoldItemId.set(null);
    this.inlineHoldReason.set('');
  }

  retryOne(item: QueueItem): void {
    if (item.entityType === 'price_action') {
      this.retryMutation.mutate(item.entityId);
    }
  }

  cancelOne(item: QueueItem): void {
    if (item.entityType === 'price_action') {
      this.cancelMutation.mutate({ actionId: item.entityId, reason: '' });
    }
  }

  // --- Inline cost editor ---

  openInlineCost(item: QueueItem): void {
    this.inlineCostItemId.set(item.entityId);
    this.inlineCostValue.set('');
  }

  confirmInlineCost(): void {
    const id = this.inlineCostItemId();
    const val = parseFloat(this.inlineCostValue().replace(',', '.'));
    if (!id || !Number.isFinite(val) || val <= 0) return;
    this.inlineCostItemId.set(null);
    this.toast.info(this.translate.instant('queues.toast.cost_saved'));
    this.afterMutation();
  }

  cancelInlineCost(): void {
    this.inlineCostItemId.set(null);
    this.inlineCostValue.set('');
  }

  // --- Bulk actions ---

  bulkApprove(): void {
    const ids = this.selectedPriceActionIds();
    if (ids.length > 0) {
      this.bulkApproveOpen.set(true);
    }
  }

  confirmBulkApprove(): void {
    const ids = this.selectedPriceActionIds();
    if (ids.length > 0) {
      this.bulkApproveMutation.mutate(ids);
    }
    this.bulkApproveOpen.set(false);
  }

  cancelBulkApprove(): void {
    this.bulkApproveOpen.set(false);
  }

  openBulkReject(): void {
    this.bulkRejectOpen.set(true);
  }

  confirmBulkReject(): void {
    const reason = this.bulkRejectReason().trim();
    if (!reason) return;
    const ids = this.selectedPriceActionIds();
    if (ids.length > 0) {
      this.bulkRejectMutation.mutate({ ids, reason });
    }
    this.bulkRejectOpen.set(false);
    this.bulkRejectReason.set('');
  }

  cancelBulkReject(): void {
    this.bulkRejectOpen.set(false);
    this.bulkRejectReason.set('');
  }

  bulkRetry(): void {
    const ids = this.selectedPriceActionIds();
    if (ids.length > 0) this.bulkRetryMutation.mutate(ids);
  }

  bulkCancel(): void {
    const ids = this.selectedPriceActionIds();
    if (ids.length > 0) this.bulkCancelMutation.mutate(ids);
  }

  bulkAcknowledge(): void {
    const ids = this.selectedItemIds();
    if (ids.length > 0) this.bulkAcknowledgeMutation.mutate(ids);
  }

  clearSelection(): void {
    this.gridApi?.deselectAll();
    this.selectedRows.set([]);
    this.queueStore.clearSelection();
  }

  focusSearch(): void {
    this.searchRef()?.nativeElement?.focus();
  }

  selectAllVisible(): void {
    this.gridApi?.selectAll();
  }

  onCellContextMenu(event: { event: Event | null; data: QueueItem | undefined }): void {
    if (!(event.event instanceof MouseEvent) || !event.data) return;
    event.event.preventDefault();
    this.contextMenu.set({
      x: event.event.clientX,
      y: event.event.clientY,
      item: event.data,
    });
  }

  closeContextMenu(): void {
    this.contextMenu.set(null);
  }

  ctxOpenDetail(): void {
    const ctx = this.contextMenu();
    if (ctx) {
      this.detailPanel.open('action', ctx.item.entityId);
      this.closeContextMenu();
    }
  }

  ctxOpenNewTab(): void {
    const ctx = this.contextMenu();
    if (ctx) {
      const wsId = this.workspaceId();
      const url = `/workspace/${wsId}/queues/${this.queueIdNum()}?item=${ctx.item.itemId}`;
      window.open(url, '_blank');
      this.closeContextMenu();
    }
  }

  ctxCopySku(): void {
    const ctx = this.contextMenu();
    if (ctx) {
      const sku = this.summary(ctx.item, 'skuCode') ?? '';
      navigator.clipboard.writeText(sku);
      this.toast.info(this.translate.instant('queues.context_menu.copied'));
      this.closeContextMenu();
    }
  }

  ctxApprove(): void {
    const ctx = this.contextMenu();
    if (ctx) {
      this.approveOne(ctx.item);
      this.closeContextMenu();
    }
  }

  ctxReject(): void {
    const ctx = this.contextMenu();
    if (ctx) {
      this.openInlineReject(ctx.item);
      this.closeContextMenu();
    }
  }

  ctxRetry(): void {
    const ctx = this.contextMenu();
    if (ctx) {
      this.retryOne(ctx.item);
      this.closeContextMenu();
    }
  }

  toggleDensity(): void {
    const compact = !this.compactMode();
    this.compactMode.set(compact);
    this.gridApi?.updateGridOptions({
      rowHeight: compact ? 32 : 44,
    });
    this.gridApi?.resetRowHeights();
  }

  toggleColumnPanel(): void {
    if (!this.gridApi) return;
    const cols = this.gridApi.getColumns() ?? [];
    const visible = cols.filter((c) => c.isVisible());
    if (visible.length === cols.length) {
      const actionCols = ['checkbox', 'actions'];
      cols.forEach((c) => {
        if (!actionCols.includes(c.getColId())) {
          this.gridApi!.setColumnsVisible([c], true);
        }
      });
    }
  }

  private markPending(entityId: number): void {
    const s = new Set(this.pendingActionIds());
    s.add(entityId);
    this.pendingActionIds.set(s);
  }

  private clearPending(entityId: number): void {
    const s = new Set(this.pendingActionIds());
    s.delete(entityId);
    this.pendingActionIds.set(s);
  }

  // --- Private ---

  private buildFilter(): QueueFilter {
    const filter: QueueFilter = {};
    const st = this.statusFilter();
    if (st) filter.status = [st];
    if (this.assignedToMe()) filter.assignedToMe = true;
    const mp = this.marketplaceFilter();
    if (mp.length) filter.marketplaceType = mp;
    const q = this.searchQuery().trim();
    if (q) filter.query = q;
    const sev = this.severityFilter();
    if (sev.length) filter.severity = sev;
    const mt = this.mismatchTypeFilter();
    if (mt.length) filter.mismatchType = mt;
    const dt = this.decisionTypeFilter();
    if (dt.length) filter.decisionType = dt;
    const as = this.actionStatusFilter();
    if (as.length) filter.actionStatus = as;
    return filter;
  }

  private handleActionError(err: unknown): void {
    if (err instanceof HttpErrorResponse && err.status === 409) {
      this.toast.warning(this.translate.instant('queues.toast.cas_conflict'));
      this.afterMutation();
      return;
    }
    this.toast.error(this.translate.instant('queues.toast.action_error'));
  }

  private afterMutation(): void {
    void this.queryClient.invalidateQueries({ queryKey: ['queueItems'] });
    void this.queryClient.invalidateQueries({ queryKey: ['queues'] });
    void this.queryClient.invalidateQueries({ queryKey: ['queue'] });
  }

  private afterBulk(): void {
    this.afterMutation();
    this.clearSelection();
  }

  private registerShortcuts(): void {
    this.shortcuts.register('ctrl+enter', () => {
      if (this.queueContext() === 'PENDING_APPROVAL' && this.selectedPriceActionIds().length > 0) {
        this.bulkApprove();
      }
    }, 'queues');
    this.shortcuts.register('ctrl+shift+enter', () => {
      if (this.queueContext() === 'PENDING_APPROVAL' && this.selectedPriceActionIds().length > 0) {
        this.openBulkReject();
      }
    }, 'queues');
    this.shortcuts.register('escape', () => {
      if (this.contextMenu()) {
        this.closeContextMenu();
      } else if (this.bulkApproveOpen()) {
        this.cancelBulkApprove();
      } else if (this.bulkRejectOpen()) {
        this.cancelBulkReject();
      } else if (this.inlineRejectItemId()) {
        this.cancelInlineReject();
      } else if (this.inlineHoldItemId()) {
        this.cancelInlineHold();
      } else if (this.inlineCostItemId()) {
        this.cancelInlineCost();
      } else {
        this.detailPanel.close();
      }
    }, 'queues');
    this.shortcuts.register('ctrl+a', () => this.selectAllVisible(), 'queues');
    this.shortcuts.register('ctrl+f', () => this.focusSearch(), 'queues');
    this.shortcuts.register('enter', () => {
      if (this.inlineRejectItemId() || this.inlineHoldItemId() || this.inlineCostItemId()) return;
      if (this.bulkRejectOpen() || this.bulkApproveOpen()) return;
      const focused = this.gridApi?.getFocusedCell();
      if (focused) {
        const row = this.gridApi?.getDisplayedRowAtIndex(focused.rowIndex);
        if (row?.data) {
          this.detailPanel.open('action', (row.data as QueueItem).entityId);
        }
      }
    }, 'queues');
  }

  // --- Column Definitions ---

  private buildColumnDefs(ctx: QueueContext): ColDef<QueueItem>[] {
    const skuCol: ColDef<QueueItem> = {
      colId: 'sku_code',
      headerName: this.translate.instant('queues.grid.sku'),
      headerTooltip: this.translate.instant('queues.grid.sku'),
      pinned: 'left',
      width: 120,
      cellClass: 'font-mono',
      valueGetter: (p) => this.summary(p.data, 'skuCode'),
      cellRenderer: (params: ICellRendererParams<QueueItem>) => {
        const v = params.value;
        if (v == null || v === '') {
          return '';
        }
        const span = document.createElement('span');
        span.className =
          'text-[var(--accent-primary)] cursor-pointer hover:underline';
        span.textContent = String(v);
        return span;
      },
      onCellClicked: (params: CellClickedEvent<QueueItem>) => {
        if (params.data) {
          this.onRowClicked(params.data);
        }
      },
    };

    const productCol: ColDef<QueueItem> = {
      colId: 'product_name',
      headerName: this.translate.instant('queues.grid.product'),
      minWidth: 200,
      flex: 2,
      valueGetter: (p) => this.summary(p.data, 'offerName'),
    };

    const mpCol: ColDef<QueueItem> = {
      colId: 'marketplace',
      headerName: this.translate.instant('queues.grid.marketplace'),
      headerTooltip: this.translate.instant('queues.grid.marketplace'),
      width: 60,
      valueGetter: (p) => this.summary(p.data, 'marketplaceType'),
    };

    const currentPriceCol: ColDef<QueueItem> = {
      colId: 'current_price',
      headerName: this.translate.instant('queues.grid.current_price'),
      headerTooltip: this.translate.instant('queues.grid.current_price'),
      width: 110,
      cellClass: 'font-mono text-right',
      valueGetter: (p) => this.summaryNum(p.data, 'currentPrice'),
      valueFormatter: (p) => formatMoney(p.value as number | null),
    };

    const costCol: ColDef<QueueItem> = {
      colId: 'cost_price',
      headerName: this.translate.instant('queues.grid.cost_price'),
      headerTooltip: this.translate.instant('queues.grid.cost_price'),
      width: 110,
      cellClass: 'font-mono text-right',
      valueGetter: (p) => this.summaryNum(p.data, 'costPrice'),
      valueFormatter: (p) => formatMoney(p.value as number | null),
    };

    const marginCol: ColDef<QueueItem> = {
      colId: 'margin_pct',
      headerName: this.translate.instant('queues.grid.margin'),
      headerTooltip: this.translate.instant('queues.grid.margin'),
      width: 80,
      cellClass: 'font-mono text-right',
      valueGetter: (p) => this.summaryNum(p.data, 'marginPct'),
      valueFormatter: (p) => formatPercent(p.value as number | null),
      cellStyle: (p) => ({ color: financeColor(p.value as number | null) }),
    };

    const createdCol: ColDef<QueueItem> = {
      colId: 'created_at',
      headerName: this.translate.instant('queues.grid.created'),
      width: 140,
      valueGetter: (p) => p.data?.createdAt,
      valueFormatter: (p) => formatDateTime(p.value as string | null, 'full'),
    };

    const actionsCol: ColDef<QueueItem> = {
      colId: 'actions',
      headerName: '',
      sortable: false,
      filter: false,
      pinned: 'right',
      flex: 0,
      width: this.actionColumnWidth(ctx),
      cellRenderer: (params: { data?: QueueItem }) =>
        params.data ? this.renderActions(params.data, ctx) : '',
    };

    const common = [skuCol, productCol, mpCol, currentPriceCol, costCol, marginCol];

    switch (ctx) {
      case 'PENDING_APPROVAL':
        return [
          ...common,
          this.col('target_price', 'queues.grid.target_price', 110, (d) =>
            formatMoney(this.summaryNum(d, 'targetPrice')),
          ),
          this.priceDeltaCol(),
          this.col('policy', 'queues.grid.policy', 160, (d) => this.summary(d, 'policyName')),
          this.executionModeBadgeCol(),
          this.col('explanation', 'queues.grid.explanation', 200, (d) => this.summary(d, 'explanationSummary')),
          createdCol,
          actionsCol,
        ];

      case 'EXECUTION_ERRORS':
        return [
          ...common,
          this.statusBadgeCol('actionStatus', 'queues.grid.action_status'),
          this.col('last_error', 'queues.grid.last_error', 200, (d) => this.summary(d, 'lastError')),
          this.col('attempts', 'queues.grid.attempts', 80, (d) => {
            const cur = this.summaryNum(d, 'attemptCount');
            const max = this.summaryNum(d, 'maxAttempts');
            return cur != null && max != null ? `${cur}/${max}` : '—';
          }),
          this.col('last_attempt', 'queues.grid.last_attempt', 140, (d) =>
            formatDateTime(this.summary(d, 'lastAttemptAt'), 'full'),
          ),
          actionsCol,
        ];

      case 'MISMATCHES':
        return [
          ...common,
          this.col('mismatch_type', 'queues.grid.mismatch_type', 100, (d) => this.summary(d, 'mismatchType')),
          this.col('expected', 'queues.grid.expected', 100, (d) => this.summary(d, 'expectedValue')),
          this.col('actual', 'queues.grid.actual', 100, (d) => this.summary(d, 'actualValue')),
          this.priceDeltaCol('deltaPct'),
          this.severityCol(),
          this.col('detected', 'queues.grid.detected', 140, (d) =>
            formatDateTime(this.summary(d, 'detectedAt'), 'full'),
          ),
          actionsCol,
        ];

      case 'NO_COST':
        return [
          ...common,
          this.col('stock', 'queues.grid.stock', 80, (d) =>
            formatInteger(this.summaryNum(d, 'availableStock')),
          ),
          this.col('revenue', 'queues.grid.revenue_30d', 110, (d) =>
            formatMoney(this.summaryNum(d, 'revenue30d')),
          ),
          this.col('velocity', 'queues.grid.velocity', 80, (d) => {
            const v = this.summaryNum(d, 'velocity14d');
            return v != null ? v.toFixed(1) : '—';
          }),
          this.col('active_policy', 'queues.grid.active_policy', 160, (d) => this.summary(d, 'activePolicy')),
          actionsCol,
        ];

      case 'CRITICAL_STOCK':
        return [
          ...common,
          this.col('stock', 'queues.grid.stock', 80, (d) =>
            formatInteger(this.summaryNum(d, 'availableStock')),
          ),
          this.col('days_cover', 'queues.grid.days_cover', 90, (d) => {
            const v = this.summaryNum(d, 'daysOfCover');
            return v != null ? v.toFixed(1) : '—';
          }),
          this.col('velocity', 'queues.grid.velocity', 80, (d) => {
            const v = this.summaryNum(d, 'velocity14d');
            return v != null ? v.toFixed(1) : '—';
          }),
          this.col('risk', 'queues.grid.risk', 90, (d) => this.summary(d, 'stockRisk')),
          actionsCol,
        ];

      case 'RECENT_DECISIONS':
        return [
          ...common,
          this.col('decision_type', 'queues.grid.decision_type', 90, (d) => this.summary(d, 'decisionType')),
          this.col('target_price', 'queues.grid.target_price', 110, (d) =>
            formatMoney(this.summaryNum(d, 'targetPrice')),
          ),
          this.priceDeltaCol(),
          this.statusBadgeCol('actionStatus', 'queues.grid.action_status'),
          this.col('policy', 'queues.grid.policy', 160, (d) => this.summary(d, 'policyName')),
          this.col('explanation', 'queues.grid.explanation', 200, (d) => this.summary(d, 'explanationSummary')),
          createdCol,
          actionsCol,
        ];

      default:
        return [
          ...common, createdCol, actionsCol,
        ];
    }
  }

  private col(
    id: string,
    headerKey: string,
    width: number,
    formatter: (data: QueueItem | undefined) => string,
  ): ColDef<QueueItem> {
    const name = this.translate.instant(headerKey);
    const def: ColDef<QueueItem> = {
      colId: id,
      headerName: name,
      width,
      valueGetter: (p) => formatter(p.data),
    };
    if (width < 140) {
      def.headerTooltip = name;
    }
    return def;
  }

  private priceDeltaCol(summaryKey = 'priceChangePct'): ColDef<QueueItem> {
    return {
      colId: 'price_change_pct',
      headerName: this.translate.instant('queues.grid.delta_pct'),
      headerTooltip: this.translate.instant('queues.grid.delta_pct'),
      width: 80,
      cellClass: 'font-mono text-right',
      valueGetter: (p) => this.summaryNum(p.data, summaryKey),
      cellRenderer: (params: { value: number | null }) => {
        const v = params.value;
        if (v === null || v === undefined) return '—';
        const abs = Math.abs(v).toFixed(1).replace('.', ',');
        if (v > 0) return `<span style="color: var(--finance-positive)">↑ ${abs}%</span>`;
        if (v < 0) return `<span style="color: var(--finance-negative)">↓ ${abs}%</span>`;
        return `<span style="color: var(--finance-zero)">→ 0%</span>`;
      },
    };
  }

  private statusBadgeCol(summaryKey: string, headerKey: string): ColDef<QueueItem> {
    const statusColors: Record<string, string> = {
      SUCCEEDED: 'success', APPROVED: 'success', SCHEDULED: 'info',
      PENDING_APPROVAL: 'warning', ON_HOLD: 'warning', IN_PROGRESS: 'info',
      FAILED: 'error', CANCELLED: 'neutral', REJECTED: 'error',
    };
    return {
      colId: summaryKey,
      headerName: this.translate.instant(headerKey),
      width: 140,
      valueGetter: (p) => this.summary(p.data, summaryKey),
      cellRenderer: (params: { value: string | null }) => {
        const st = params.value;
        if (!st) return '';
        const color = statusColors[st] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        const label = this.translate.instant(`grid.action_status.${st}`);
        return renderBadge(label, cssVar);
      },
    };
  }

  private severityCol(): ColDef<QueueItem> {
    return {
      colId: 'severity',
      headerName: this.translate.instant('queues.grid.severity'),
      headerTooltip: this.translate.instant('queues.grid.severity'),
      width: 100,
      valueGetter: (p) => this.summary(p.data, 'severity'),
      valueFormatter: (params) =>
        params.value
          ? this.translate.instant('queues.severity.' + params.value)
          : '—',
      cellRenderer: (params: { value: string | null }) => {
        const v = params.value;
        if (!v) return '';
        const color = v === 'CRITICAL' ? 'error' : v === 'WARNING' ? 'warning' : 'neutral';
        const cssVar = `var(--status-${color})`;
        const key = 'queues.severity.' + v;
        const label = this.translate.instant(key);
        return renderBadge(label !== key ? label : v, cssVar);
      },
    };
  }

  private executionModeBadgeCol(): ColDef<QueueItem> {
    return {
      colId: 'mode',
      headerName: this.translate.instant('queues.grid.mode'),
      headerTooltip: this.translate.instant('queues.grid.mode'),
      width: 110,
      valueGetter: (p) => this.summary(p.data, 'executionMode'),
      cellRenderer: (params: { value: string | null }) => {
        const v = params.value;
        if (!v) return '';
        const isSimulated = v === 'SIMULATED';
        const cssVar = isSimulated ? 'var(--status-neutral)' : 'var(--status-info)';
        const label = this.translate.instant(`queues.grid.mode_${v.toLowerCase()}`);
        return renderBadge(label, cssVar);
      },
    };
  }

  private actionColumnWidth(ctx: QueueContext): number {
    switch (ctx) {
      case 'PENDING_APPROVAL': return 200;
      case 'EXECUTION_ERRORS': return 160;
      case 'MISMATCHES': return 160;
      default: return 120;
    }
  }

  private renderActions(item: QueueItem, ctx: QueueContext): HTMLElement {
    const wrap = document.createElement('div');
    wrap.className = 'flex flex-wrap items-center gap-1 py-1';

    if (this.pendingActionIds().has(item.entityId)) {
      const spinner = document.createElement('div');
      spinner.className = 'h-4 w-4 animate-spin rounded-full border-2 border-[var(--accent-primary)] border-t-transparent';
      wrap.appendChild(spinner);
      return wrap;
    }

    const addBtn = (
      labelKey: string,
      onClick: () => void,
      variant: 'primary' | 'danger' | 'ghost' = 'ghost',
    ) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.textContent = this.translate.instant(labelKey);
      b.className = this.actionBtnClass(variant);
      b.addEventListener('click', (e) => {
        e.stopPropagation();
        onClick();
      });
      wrap.appendChild(b);
    };

    switch (ctx) {
      case 'PENDING_APPROVAL':
        if (this.rbac.canApproveActions()) {
          addBtn('queues.actions.approve', () => this.approveOne(item), 'primary');
          addBtn('queues.actions.reject', () => this.openInlineReject(item), 'danger');
        }
        if (this.rbac.canOperateActions()) {
          addBtn('queues.actions.hold', () => this.holdOne(item));
        }
        break;

      case 'EXECUTION_ERRORS':
        if (this.rbac.canApproveActions()) {
          addBtn('queues.actions.retry', () => this.retryOne(item), 'primary');
        }
        if (this.rbac.canOperateActions()) {
          addBtn('queues.actions.cancel', () => this.cancelOne(item), 'danger');
        }
        break;

      case 'MISMATCHES':
        if (this.rbac.canOperateActions()) {
          addBtn('queues.actions.acknowledge', () => this.claimMutation.mutate(item.itemId), 'primary');
        }
        break;

      case 'NO_COST':
        if (this.rbac.canOperateActions()) {
          addBtn('queues.actions.set_cost', () => this.openInlineCost(item), 'primary');
        }
        break;

      case 'CRITICAL_STOCK':
      case 'RECENT_DECISIONS':
        addBtn('queues.actions.open_detail', () => this.detailPanel.open('action', item.entityId));
        break;

      default:
        if (this.rbac.canOperateActions()) {
          addBtn('queues.actions.claim', () => this.claimMutation.mutate(item.itemId), 'primary');
          addBtn('queues.actions.done', () => this.completeMutation.mutate(item.itemId));
          addBtn('queues.actions.dismiss', () => this.dismissMutation.mutate(item.itemId));
        }
        break;
    }

    return wrap;
  }

  private actionBtnClass(variant: 'primary' | 'danger' | 'ghost'): string {
    const base = 'rounded-[var(--radius-md)] px-2 py-1 text-[11px] font-medium transition-colors';
    switch (variant) {
      case 'primary':
        return `${base} bg-[var(--accent-primary)] text-white hover:bg-[var(--accent-primary-hover)]`;
      case 'danger':
        return `${base} bg-[var(--status-error)] text-white hover:opacity-90`;
      case 'ghost':
        return `${base} text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]`;
    }
  }

  private summary(data: QueueItem | undefined, key: string): string {
    const v = data?.entitySummary?.[key];
    return v != null ? String(v) : '';
  }

  private summaryNum(data: QueueItem | undefined, key: string): number | null {
    const v = data?.entitySummary?.[key];
    if (v === null || v === undefined) return null;
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }
}
