import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AgGridAngular } from 'ag-grid-angular';
import {
  ColDef,
  GridApi,
  GridReadyEvent,
  ICellRendererParams,
  SelectionChangedEvent,
} from 'ag-grid-community';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import {
  injectMutation,
  injectQuery,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { LucideAngularModule, CheckCircle2 } from 'lucide-angular';
import { lastValueFrom } from 'rxjs';

import { QueueApiService } from '@core/api/queue-api.service';
import {
  Queue,
  QueueFilter,
  QueueItem,
  QueueItemStatus,
  QueueType,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { QueueStore } from '@shared/stores/queue.store';
import { ToastService } from '@shared/shell/toast/toast.service';

@Component({
  selector: 'dp-queue-items-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AgGridAngular, TranslatePipe, FormsModule, LucideAngularModule],
  templateUrl: './queue-items-page.component.html',
})
export class QueueItemsPageComponent {
  readonly defaultColDef: ColDef<QueueItem> = {
    flex: 1,
    minWidth: 96,
    sortable: false,
    resizable: true,
  };

  private readonly queueApi = inject(QueueApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly queueStore = inject(QueueStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);

  readonly queueId = input.required<string>();
  readonly checkIcon = CheckCircle2;

  readonly statusOptions: QueueItemStatus[] = [
    'PENDING',
    'IN_PROGRESS',
    'DONE',
    'DISMISSED',
  ];

  readonly pageSizeOptions = [20, 50, 100] as const;

  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly statusFilter = signal<QueueItemStatus | ''>('');
  readonly assignedToMe = signal(false);
  readonly searchQuery = signal('');

  readonly selectedRows = signal<QueueItem[]>([]);
  readonly bulkRejectOpen = signal(false);
  readonly bulkRejectReason = signal('');

  readonly queueIdNum = computed(() => Number(this.queueId()));

  readonly workspaceId = computed(() => this.wsStore.currentWorkspaceId());

  readonly queueQuery = injectQuery(() => {
    const ws = this.workspaceId();
    const qid = this.queueIdNum();
    return {
      queryKey: ['queue', ws, qid] as const,
      queryFn: () => lastValueFrom(this.queueApi.getQueue(ws!, qid)),
      enabled: ws != null && ws > 0 && Number.isFinite(qid) && qid > 0,
    };
  });

  readonly itemsQuery = injectQuery(() => {
    const ws = this.workspaceId();
    const qid = this.queueIdNum();
    const page = this.pageIndex();
    const size = this.pageSize();
    const st = this.statusFilter();
    const me = this.assignedToMe();
    const q = this.searchQuery().trim();
    const filter: QueueFilter = {};
    if (st) {
      filter.status = [st];
    }
    if (me) {
      filter.assignedToMe = true;
    }
    if (q) {
      filter.query = q;
    }
    return {
      queryKey: ['queueItems', ws, qid, page, size, filter] as const,
      queryFn: () =>
        lastValueFrom(
          this.queueApi.listItems(ws!, qid, filter, page, size, 'created_at', 'ASC'),
        ),
      enabled: ws != null && ws > 0 && Number.isFinite(qid) && qid > 0,
    };
  });

  readonly claimMutation = injectMutation(() => ({
    mutationFn: (itemId: number) => {
      const ws = this.workspaceId()!;
      const qid = this.queueIdNum();
      return lastValueFrom(this.queueApi.claimItem(ws, qid, itemId));
    },
    onSuccess: () => this.afterMutation(),
    onError: () =>
      this.toast.error(this.translate.instant('queues.page.action_error')),
  }));

  readonly completeMutation = injectMutation(() => ({
    mutationFn: (itemId: number) => {
      const ws = this.workspaceId()!;
      const qid = this.queueIdNum();
      return lastValueFrom(this.queueApi.completeItem(ws, qid, itemId));
    },
    onSuccess: () => this.afterMutation(),
    onError: () =>
      this.toast.error(this.translate.instant('queues.page.action_error')),
  }));

  readonly dismissMutation = injectMutation(() => ({
    mutationFn: (itemId: number) => {
      const ws = this.workspaceId()!;
      const qid = this.queueIdNum();
      return lastValueFrom(this.queueApi.dismissItem(ws, qid, itemId));
    },
    onSuccess: () => this.afterMutation(),
    onError: () =>
      this.toast.error(this.translate.instant('queues.page.action_error')),
  }));

  readonly bulkApproveMutation = injectMutation(() => ({
    mutationFn: (ids: number[]) => {
      const ws = this.workspaceId()!;
      return lastValueFrom(this.queueApi.bulkApprove(ws, ids));
    },
    onSuccess: () => this.afterBulk(),
    onError: () =>
      this.toast.error(this.translate.instant('queues.page.action_error')),
  }));

  readonly bulkRejectMutation = injectMutation(() => ({
    mutationFn: (payload: { ids: number[]; reason: string }) => {
      const ws = this.workspaceId()!;
      return lastValueFrom(
        this.queueApi.bulkReject(ws, payload.ids, payload.reason),
      );
    },
    onSuccess: () => this.afterBulk(),
    onError: () =>
      this.toast.error(this.translate.instant('queues.page.action_error')),
  }));

  readonly columnDefs = computed(() =>
    this.buildColumnDefs(this.queueQuery.data()?.queueType ?? 'ATTENTION'),
  );

  readonly rowData = computed(() => this.itemsQuery.data()?.content ?? []);

  readonly totalElements = computed(
    () => this.itemsQuery.data()?.totalElements ?? 0,
  );

  readonly totalPages = computed(() =>
    Math.max(1, this.itemsQuery.data()?.totalPages ?? 1),
  );

  readonly selectedPriceActionIds = computed(() =>
    this.selectedRows()
      .filter((r) => r.entityType === 'price_action')
      .map((r) => r.entityId),
  );

  readonly showDecisionBulk = computed(
    () => this.queueQuery.data()?.queueType === 'DECISION',
  );

  private gridApi: GridApi<QueueItem> | null = null;

  constructor() {
    effect(() => {
      const id = this.queueIdNum();
      if (Number.isFinite(id) && id > 0) {
        this.queueStore.selectQueue(id);
      }
    });
  }

  onGridReady(event: GridReadyEvent<QueueItem>): void {
    this.gridApi = event.api;
  }

  onSelectionChanged(event: SelectionChangedEvent<QueueItem>): void {
    this.selectedRows.set(event.api.getSelectedRows());
  }

  getRowId = (params: { data?: QueueItem }): string =>
    params.data ? String(params.data.itemId) : '';

  onStatusChange(value: string): void {
    this.statusFilter.set(value as QueueItemStatus | '');
    this.pageIndex.set(0);
  }

  onAssignedToggle(): void {
    this.assignedToMe.update((v) => !v);
    this.pageIndex.set(0);
  }

  applySearch(value: string): void {
    this.searchQuery.set(value);
    this.pageIndex.set(0);
  }

  setPageSize(n: number): void {
    this.pageSize.set(n);
    this.pageIndex.set(0);
  }

  prevPage(): void {
    this.pageIndex.update((p) => Math.max(0, p - 1));
  }

  nextPage(): void {
    const max = (this.itemsQuery.data()?.totalPages ?? 1) - 1;
    this.pageIndex.update((p) => Math.min(max, p + 1));
  }

  clearSelection(): void {
    this.gridApi?.deselectAll();
    this.selectedRows.set([]);
  }

  claim(item: QueueItem): void {
    this.claimMutation.mutate(item.itemId);
  }

  complete(item: QueueItem): void {
    this.completeMutation.mutate(item.itemId);
  }

  dismiss(item: QueueItem): void {
    this.dismissMutation.mutate(item.itemId);
  }

  approveOne(item: QueueItem): void {
    const id = this.actionIdForBulk(item);
    if (id != null) {
      this.bulkApproveMutation.mutate([id]);
    }
  }

  rejectOne(item: QueueItem): void {
    const id = this.actionIdForBulk(item);
    if (id != null) {
      this.bulkRejectMutation.mutate({
        ids: [id],
        reason: this.translate.instant('queues.page.reject_default_reason'),
      });
    }
  }

  bulkApprove(): void {
    const ids = this.selectedPriceActionIds();
    if (ids.length > 0) {
      this.bulkApproveMutation.mutate(ids);
    }
  }

  openBulkReject(): void {
    this.bulkRejectOpen.set(true);
  }

  confirmBulkReject(): void {
    const reason = this.bulkRejectReason().trim();
    if (!reason) {
      return;
    }
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

  private actionIdForBulk(item: QueueItem): number | null {
    return item.entityType === 'price_action' ? item.entityId : null;
  }

  private afterMutation(): void {
    void this.queryClient.invalidateQueries({ queryKey: ['queueItems'] });
    void this.queryClient.invalidateQueries({ queryKey: ['queues'] });
    void this.queryClient.invalidateQueries({ queryKey: ['queue'] });
    this.clearSelection();
    this.toast.success(this.translate.instant('queues.page.action_ok'));
  }

  private afterBulk(): void {
    void this.queryClient.invalidateQueries({ queryKey: ['queueItems'] });
    void this.queryClient.invalidateQueries({ queryKey: ['queues'] });
    void this.queryClient.invalidateQueries({ queryKey: ['queue'] });
    this.clearSelection();
    this.toast.success(this.translate.instant('queues.page.bulk_ok'));
  }

  private buildColumnDefs(queueType: QueueType): ColDef<QueueItem>[] {
    const base: ColDef<QueueItem>[] = [
      {
        field: 'status',
        headerName: this.translate.instant('queues.grid.status'),
        minWidth: 120,
        valueFormatter: (p) => {
          const v = p.value;
          return v
            ? this.translate.instant(`queues.item_status.${String(v)}`)
            : '';
        },
      },
      {
        field: 'entityType',
        headerName: this.translate.instant('queues.grid.entity_type'),
        minWidth: 140,
      },
      {
        field: 'entityId',
        headerName: this.translate.instant('queues.grid.entity_id'),
        maxWidth: 110,
      },
      ...this.summaryColumns(queueType),
      {
        field: 'createdAt',
        headerName: this.translate.instant('queues.grid.created'),
        minWidth: 160,
        valueFormatter: (p) =>
          p.value ? format(new Date(String(p.value)), 'PPp', { locale: ru }) : '',
      },
      {
        colId: 'actions',
        headerName: this.translate.instant('queues.grid.actions'),
        sortable: false,
        filter: false,
        pinned: 'right',
        flex: 0,
        width: this.actionColumnWidth(queueType),
        cellRenderer: (params: ICellRendererParams<QueueItem>) => {
          return this.renderActions(params, queueType);
        },
      },
    ];
    return base;
  }

  private actionColumnWidth(queueType: QueueType): number {
    switch (queueType) {
      case 'DECISION':
        return 200;
      case 'PROCESSING':
        return 180;
      default:
        return 220;
    }
  }

  private summaryColumns(queueType: QueueType): ColDef<QueueItem>[] {
    const keys =
      queueType === 'ATTENTION'
        ? ['offerName', 'skuCode', 'offerStatus', 'marketplaceType']
        : queueType === 'DECISION'
          ? ['offerName', 'skuCode', 'actionStatus']
          : ['offerName', 'skuCode', 'actionStatus', 'lastError'];
    return keys.map((key) => ({
      colId: `sum_${key}`,
      headerName: key,
      sortable: false,
      valueGetter: (p) => {
        const v = p.data?.entitySummary[key];
        return v == null ? '' : String(v);
      },
    }));
  }

  private renderActions(
    params: ICellRendererParams<QueueItem>,
    queueType: QueueType,
  ): HTMLElement {
    const wrap = document.createElement('div');
    wrap.className = 'flex flex-wrap gap-1 py-1';

    const item = params.data;
    if (!item) {
      return wrap;
    }

    const addBtn = (labelKey: string, onClick: () => void, variant: 'primary' | 'ghost' = 'ghost') => {
      const b = document.createElement('button');
      b.type = 'button';
      b.textContent = this.translate.instant(labelKey);
      b.className =
        variant === 'primary'
          ? 'rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-2 py-1 text-[11px] font-medium text-white hover:bg-[var(--accent-primary-hover)]'
          : 'rounded-[var(--radius-md)] px-2 py-1 text-[11px] text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]';
      b.addEventListener('click', (e) => {
        e.stopPropagation();
        onClick();
      });
      wrap.appendChild(b);
    };

    if (queueType === 'DECISION' && item.entityType === 'price_action') {
      addBtn('queues.actions.approve', () => this.approveOne(item), 'primary');
      addBtn('queues.actions.reject', () => this.rejectOne(item));
      return wrap;
    }

    if (queueType === 'PROCESSING') {
      addBtn('queues.actions.retry', () => this.claim(item), 'primary');
      addBtn('queues.actions.cancel', () => this.dismiss(item));
      return wrap;
    }

    addBtn('queues.actions.claim', () => this.claim(item), 'primary');
    addBtn('queues.actions.done', () => this.complete(item));
    addBtn('queues.actions.dismiss', () => this.dismiss(item));

    return wrap;
  }
}
