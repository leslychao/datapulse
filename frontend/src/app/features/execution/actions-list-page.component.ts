import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  HostListener,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
  keepPreviousData,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { FormsModule } from '@angular/forms';

import { ClipboardList, Clock, Play, XCircle, Download } from 'lucide-angular';

import { ActionApiService } from '@core/api/action-api.service';
import { ConnectionApiService } from '@core/api/connection-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { translateApiErrorMessage } from '@core/i18n/translate-api-error';
import { ActionFilter, ActionSummary } from '@core/models';
import { formatMoney, formatRelativeTime } from '@shared/utils/format.utils';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { DetailPanelService } from '@shared/services/detail-panel.service';
import { ToastService } from '@shared/shell/toast/toast.service';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { StatusColor } from '@shared/components/status-badge.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { FormModalComponent } from '@shared/components/form-modal.component';

const ACTION_STATUS_COLOR: Record<string, StatusColor> = {
  PENDING_APPROVAL: 'info', APPROVED: 'info', ON_HOLD: 'warning',
  SCHEDULED: 'info', EXECUTING: 'warning', RECONCILIATION_PENDING: 'warning',
  RETRY_SCHEDULED: 'warning', SUCCEEDED: 'success', FAILED: 'error',
  EXPIRED: 'neutral', CANCELLED: 'neutral', SUPERSEDED: 'neutral',
};

const ACTION_STATUSES = [
  'PENDING_APPROVAL', 'APPROVED', 'ON_HOLD', 'SCHEDULED', 'EXECUTING',
  'RECONCILIATION_PENDING', 'RETRY_SCHEDULED', 'SUCCEEDED', 'FAILED',
  'EXPIRED', 'CANCELLED', 'SUPERSEDED',
] as const;

const CANCELLABLE = new Set([
  'PENDING_APPROVAL', 'APPROVED', 'ON_HOLD', 'SCHEDULED', 'RETRY_SCHEDULED',
]);

interface ContextMenuState {
  visible: boolean;
  x: number;
  y: number;
  row: ActionSummary | null;
}

@Component({
  selector: 'dp-actions-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe, FormsModule,
    KpiCardComponent, FilterBarComponent, DataGridComponent,
    EmptyStateComponent, ConfirmationModalComponent, FormModalComponent,
  ],
  template: `
    <div class="flex h-full flex-col">
      <!-- KPI Strip -->
      <div class="flex flex-wrap gap-3 px-4 pt-3">
        <dp-kpi-card
          [label]="'execution.kpi.total' | translate"
          [value]="kpiTotal()"
          [icon]="ClipboardListIcon"
          accent="primary"
          [loading]="kpiQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'execution.kpi.pending' | translate"
          [value]="kpiPending()"
          [icon]="ClockIcon"
          accent="warning"
          [loading]="kpiQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'execution.kpi.executing' | translate"
          [value]="kpiExecuting()"
          [icon]="PlayIcon"
          accent="info"
          [loading]="kpiQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'execution.kpi.failed' | translate"
          [value]="kpiFailed()"
          [icon]="XCircleIcon"
          accent="error"
          [loading]="kpiQuery.isPending()"
        />
      </div>

      <!-- Toolbar: Filters + Export -->
      <div class="flex items-center gap-3 px-4 pt-2">
        <div class="flex-1">
          <dp-filter-bar
            [filters]="filterConfigs()"
            [values]="filterValues()"
            (filtersChanged)="onFiltersChanged($event)"
          />
        </div>
        @if (rbac.canExport()) {
          <button
            (click)="exportCsv()"
            [disabled]="exportPending()"
            class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] px-3 py-1.5 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)] disabled:opacity-50"
            [attr.aria-label]="'execution.list.export' | translate"
          >
            {{ 'execution.list.export' | translate }}
          </button>
        }
      </div>

      <!-- Data Grid -->
      <div class="flex-1 px-4 py-2">
        @if (actionsQuery.isError()) {
          <dp-empty-state
            [message]="'execution.list.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="actionsQuery.refetch()"
          />
        } @else if (!actionsQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="hasActiveFilters() ? ('execution.list.empty_filtered' | translate) : ('execution.list.empty' | translate)"
            [actionLabel]="hasActiveFilters() ? ('filter_bar.reset_all' | translate) : ''"
            (action)="onFiltersChanged({})"
          />
        } @else {
          <dp-data-grid
            [columnDefs]="columnDefs"
            [rowData]="rows()"
            [loading]="actionsQuery.isPending()"
            [pagination]="true"
            [pageSize]="50"
            [rowSelection]="'multiple'"
            [getRowId]="getRowId"
            [height]="'100%'"
            [enableFlash]="true"
            [contextMenuEnabled]="true"
            (rowClicked)="onRowClicked($event)"
            (cellDoubleClicked)="onRowDoubleClicked($event)"
            (selectionChanged)="onSelectionChanged($event)"
            (contextMenu)="onContextMenu($event)"
          />
        }
      </div>

      <!-- Bulk Action Bar -->
      @if (selectedRows().length > 0) {
        <div
          class="flex items-center gap-4 border-t border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2.5"
          aria-live="polite"
        >
          <span class="text-sm text-[var(--text-secondary)]">
            {{ selectedRows().length }} {{ 'execution.bulk.selected' | translate }}
          </span>
          @if (rbac.canApproveActions()) {
            <button
              (click)="openBulkApprove()"
              class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
            >
              {{ 'execution.bulk.approve_selected' | translate }}
            </button>
          }
          @if (rbac.canOperateActions()) {
            <button
              (click)="openBulkCancel()"
              class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--status-error)] px-4 py-1.5 text-sm font-medium text-[var(--status-error)] transition-colors hover:bg-[color-mix(in_srgb,var(--status-error)_8%,transparent)]"
            >
              {{ 'execution.bulk.cancel_selected' | translate }}
            </button>
          }
          <button
            (click)="clearSelection()"
            class="cursor-pointer rounded-[var(--radius-md)] px-3 py-1.5 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
          >
            {{ 'actions.cancel' | translate }}
          </button>
        </div>
      }
    </div>

    <!-- Bulk Approve Modal -->
    <dp-confirmation-modal
      [open]="showBulkApproveModal()"
      [title]="'execution.bulk.approve_title' | translate"
      [message]="bulkApproveMessage()"
      [confirmLabel]="bulkApproveLabel()"
      (confirmed)="executeBulkApprove()"
      (cancelled)="showBulkApproveModal.set(false)"
    />

    <!-- Bulk Cancel Modal -->
    <dp-form-modal
      [title]="'execution.bulk.cancel_title' | translate"
      [isOpen]="showBulkCancelModal()"
      [submitLabel]="bulkCancelLabel()"
      [isPending]="bulkCancelMutation.isPending()"
      [submitDisabled]="bulkCancelReason().length < 5"
      (submit)="executeBulkCancel()"
      (close)="showBulkCancelModal.set(false)"
    >
      <div class="flex flex-col gap-3">
        <p class="text-sm text-[var(--text-secondary)]">{{ bulkCancelMessage() }}</p>
        <label class="text-sm text-[var(--text-secondary)]">{{ 'execution.detail.reason_label' | translate }} *</label>
        <textarea
          [ngModel]="bulkCancelReason()"
          (ngModelChange)="bulkCancelReason.set($event)"
          class="min-h-[80px] max-h-[200px] resize-y rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
          [placeholder]="'execution.detail.cancel_placeholder' | translate"
        ></textarea>
      </div>
    </dp-form-modal>

    <!-- Context Menu -->
    @if (ctxMenu().visible) {
      <div
        class="fixed z-50 min-w-[180px] rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] py-1 shadow-[var(--shadow-md)]"
        [style.left.px]="ctxMenu().x"
        [style.top.px]="ctxMenu().y"
      >
        <button
          (click)="ctxOpenFullPage()"
          class="flex w-full cursor-pointer items-center gap-2 px-3 py-1.5 text-sm text-[var(--text-primary)] hover:bg-[var(--bg-secondary)]"
        >
          {{ 'execution.context_menu.open_new_tab' | translate }}
        </button>
        @if (rbac.canApproveActions() && ctxMenu().row?.status === 'PENDING_APPROVAL') {
          <button
            (click)="ctxApprove()"
            class="flex w-full cursor-pointer items-center gap-2 px-3 py-1.5 text-sm text-[var(--text-primary)] hover:bg-[var(--bg-secondary)]"
          >
            {{ 'execution.context_menu.approve' | translate }}
          </button>
        }
        @if (rbac.canOperateActions() && ctxMenu().row?.status === 'APPROVED') {
          <button
            (click)="ctxHold()"
            class="flex w-full cursor-pointer items-center gap-2 px-3 py-1.5 text-sm text-[var(--text-primary)] hover:bg-[var(--bg-secondary)]"
          >
            {{ 'execution.context_menu.hold' | translate }}
          </button>
        }
        @if (rbac.canOperateActions() && canCancelRow(ctxMenu().row)) {
          <button
            (click)="ctxCancel()"
            class="flex w-full cursor-pointer items-center gap-2 px-3 py-1.5 text-sm text-[var(--status-error)] hover:bg-[var(--bg-secondary)]"
          >
            {{ 'execution.context_menu.cancel' | translate }}
          </button>
        }
        <div class="my-1 border-t border-[var(--border-default)]"></div>
        <button
          (click)="ctxCopySku()"
          class="flex w-full cursor-pointer items-center gap-2 px-3 py-1.5 text-sm text-[var(--text-secondary)] hover:bg-[var(--bg-secondary)]"
        >
          {{ 'execution.context_menu.copy_sku' | translate }}
        </button>
      </div>
    }
  `,
})
export class ActionsListPageComponent {
  private readonly actionApi = inject(ActionApiService);
  private readonly connectionApi = inject(ConnectionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  private readonly translate = inject(TranslateService);
  private readonly detailPanel = inject(DetailPanelService);
  protected readonly rbac = inject(RbacService);

  protected readonly ClipboardListIcon = ClipboardList;
  protected readonly ClockIcon = Clock;
  protected readonly PlayIcon = Play;
  protected readonly XCircleIcon = XCircle;
  protected readonly DownloadIcon = Download;

  readonly filterValues = signal<Record<string, any>>({});
  readonly selectedRows = signal<ActionSummary[]>([]);
  readonly showBulkApproveModal = signal(false);
  readonly showBulkCancelModal = signal(false);
  readonly bulkCancelReason = signal('');
  readonly exportPending = signal(false);
  readonly currentPage = signal(0);
  readonly currentSort = signal('createdAt,desc');
  readonly ctxMenu = signal<ContextMenuState>({ visible: false, x: 0, y: 0, row: null });

  @HostListener('document:click')
  @HostListener('document:keydown.escape')
  closeContextMenu(): void {
    if (this.ctxMenu().visible) {
      this.ctxMenu.set({ visible: false, x: 0, y: 0, row: null });
    }
  }

  readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections'],
    queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
    staleTime: 120_000,
  }));

  readonly kpiQuery = injectQuery(() => ({
    queryKey: ['actions-kpi', this.wsStore.currentWorkspaceId()],
    queryFn: () =>
      lastValueFrom(this.actionApi.getActionsKpi(this.wsStore.currentWorkspaceId()!)),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
    refetchInterval: 60_000,
  }));

  readonly filterConfigs = computed<FilterConfig[]>(() => {
    const connectionOptions = (this.connectionsQuery.data() ?? []).map(c => ({
      value: String(c.id),
      label: c.name,
    }));
    return [
      {
        key: 'connectionId',
        label: 'execution.filter.connection',
        type: 'select' as const,
        options: connectionOptions,
      },
      {
        key: 'status',
        label: 'execution.filter.status',
        type: 'multi-select' as const,
        options: ACTION_STATUSES.map(value => ({
          value,
          label: `grid.action_status.${value}`,
        })),
      },
      {
        key: 'executionMode',
        label: 'execution.filter.execution_mode',
        type: 'select' as const,
        options: (['LIVE', 'SIMULATED'] as const).map(value => ({
          value,
          label: `pricing.decisions.execution_mode.${value}`,
        })),
      },
      {
        key: 'search',
        label: 'execution.filter.search',
        type: 'text' as const,
      },
      {
        key: 'period',
        label: 'execution.filter.period',
        type: 'date-range' as const,
      },
    ];
  });

  readonly columnDefs = [
    {
      headerCheckboxSelection: true,
      checkboxSelection: true,
      width: 40,
      pinned: 'left' as const,
      sortable: false,
      suppressMovable: true,
    },
    {
      headerName: this.translate.instant('execution.col.offer'),
      field: 'offerName',
      minWidth: 250,
      pinned: 'left' as const,
      sortable: true,
      cellRenderer: (params: any) => {
        if (!params.data) return '';
        return `<div class="leading-tight py-1.5">
          <div class="font-medium text-[var(--text-primary)]">${params.data.offerName}</div>
          <div class="text-[11px] text-[var(--text-secondary)]">${params.data.sku}</div>
        </div>`;
      },
    },
    {
      headerName: this.translate.instant('execution.col.target_price'),
      field: 'targetPrice',
      width: 110,
      sortable: true,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: any) => this.formatPrice(params.value),
    },
    {
      headerName: this.translate.instant('execution.col.current_price'),
      field: 'currentPriceAtCreation',
      width: 110,
      sortable: true,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: any) => this.formatPrice(params.value),
    },
    {
      headerName: this.translate.instant('execution.col.price_delta'),
      field: 'priceDeltaPct',
      width: 80,
      sortable: true,
      cellClass: 'font-mono text-right',
      cellRenderer: (params: any) => {
        const v = params.value;
        if (v === null || v === undefined) return '—';
        const abs = Math.abs(v).toFixed(1).replace('.', ',');
        if (v > 0) return `<span style="color: var(--finance-positive)">↑ ${abs}%</span>`;
        if (v < 0) return `<span style="color: var(--finance-negative)">↓ ${abs}%</span>`;
        return `<span style="color: var(--finance-zero)">→ 0%</span>`;
      },
    },
    {
      headerName: this.translate.instant('execution.col.status'),
      field: 'status',
      width: 140,
      sortable: true,
      cellRenderer: (params: any) => {
        const st = params.value as string;
        const label = this.translate.instant(`grid.action_status.${st}`);
        const color = ACTION_STATUS_COLOR[st] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        return `<span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                  style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}">
          <span class="inline-block h-1.5 w-1.5 rounded-full" style="background-color: ${cssVar}"></span>
          ${label}
        </span>`;
      },
      cellStyle: (params: any) => {
        const st = params.value;
        if (st === 'FAILED') return { 'border-left': '2px solid var(--status-error)' };
        if (st === 'PENDING_APPROVAL') return { 'border-left': '2px solid var(--status-info)' };
        if (st === 'ON_HOLD') return { 'background-color': 'var(--bg-tertiary)' };
        return {};
      },
    },
    {
      headerName: this.translate.instant('execution.col.execution_mode'),
      field: 'executionMode',
      width: 80,
      sortable: true,
      cellRenderer: (params: any) => {
        const mode = params.value as string;
        const label =
          mode === 'SIMULATED'
            ? this.translate.instant('pricing.decisions.execution_mode.SIMULATED')
            : this.translate.instant('pricing.decisions.execution_mode.LIVE');
        if (mode === 'SIMULATED') {
          return `<span class="rounded-full border border-dashed border-[var(--border-default)] px-2 py-0.5 text-[11px] text-[var(--text-secondary)]">${label}</span>`;
        }
        return `<span class="text-[var(--text-primary)] text-[11px]">${label}</span>`;
      },
    },
    {
      headerName: this.translate.instant('execution.col.attempts'),
      field: 'attemptCount',
      width: 70,
      sortable: true,
      cellClass: 'font-mono text-center',
      valueFormatter: (params: any) => {
        if (!params.data) return '';
        return `${params.data.attemptCount}/${params.data.maxAttempts}`;
      },
    },
    {
      headerName: this.translate.instant('execution.col.created_at'),
      field: 'createdAt',
      width: 120,
      sortable: true,
      sort: 'desc' as const,
      valueFormatter: (params: any) => this.formatRelativeTime(params.value),
    },
  ];

  private readonly filter = computed<ActionFilter>(() => {
    const vals = this.filterValues();
    const f: ActionFilter = {};
    if (vals['connectionId']) f.connectionId = Number(vals['connectionId']);
    if (vals['status']?.length) f.status = vals['status'];
    if (vals['executionMode']) f.executionMode = vals['executionMode'];
    if (vals['search']) f.search = vals['search'];
    if (vals['period']?.from) f.from = vals['period'].from;
    if (vals['period']?.to) f.to = vals['period'].to;
    return f;
  });

  readonly actionsQuery = injectQuery(() => ({
    queryKey: ['actions', this.wsStore.currentWorkspaceId(), this.filter(), this.currentPage(), this.currentSort()],
    queryFn: () =>
      lastValueFrom(
        this.actionApi.listActions(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.currentPage(),
          50,
          this.currentSort(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
    refetchInterval: 60_000,
    placeholderData: keepPreviousData,
  }));

  readonly rows = computed(() => this.actionsQuery.data()?.content ?? []);

  readonly hasActiveFilters = computed(() =>
    Object.values(this.filterValues()).some(
      (v) => v !== '' && v !== null && v !== undefined && (!Array.isArray(v) || v.length > 0),
    ),
  );

  readonly kpiTotal = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi ? kpi.total.toLocaleString('ru-RU') : null;
  });

  readonly kpiPending = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi && kpi.pending > 0 ? kpi.pending.toLocaleString('ru-RU') : null;
  });

  readonly kpiExecuting = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi && kpi.executing > 0 ? kpi.executing.toLocaleString('ru-RU') : null;
  });

  readonly kpiFailed = computed(() => {
    const kpi = this.kpiQuery.data();
    return kpi && kpi.failed > 0 ? kpi.failed.toLocaleString('ru-RU') : null;
  });

  private readonly pendingSelected = computed(() =>
    this.selectedRows().filter((r) => r.status === 'PENDING_APPROVAL'),
  );

  private readonly cancellableSelected = computed(() =>
    this.selectedRows().filter((r) => CANCELLABLE.has(r.status)),
  );

  readonly bulkApproveMessage = computed(() => {
    const total = this.selectedRows().length;
    const eligible = this.pendingSelected().length;
    const skipped = total - eligible;
    if (eligible === 0) return this.translate.instant('execution.bulk.no_eligible');
    let msg = this.translate.instant('execution.bulk.approve_confirm', { count: eligible });
    if (skipped > 0) {
      msg += '\n' + this.translate.instant('execution.bulk.approve_partial', { total, eligible, skipped });
    }
    return msg;
  });

  readonly bulkApproveLabel = computed(() => {
    const count = this.pendingSelected().length;
    return count > 0
      ? this.translate.instant('execution.bulk.approve_count', { count })
      : this.translate.instant('execution.bulk.approve');
  });

  readonly bulkCancelMessage = computed(() => {
    const total = this.selectedRows().length;
    const eligible = this.cancellableSelected().length;
    const skipped = total - eligible;
    if (eligible === 0) return this.translate.instant('execution.bulk.no_cancellable');
    let msg = this.translate.instant('execution.bulk.cancel_confirm', { count: eligible });
    if (skipped > 0) {
      msg += '\n' + this.translate.instant('execution.bulk.cancel_partial', { total, eligible, skipped });
    }
    return msg;
  });

  readonly bulkCancelLabel = computed(() => {
    const count = this.cancellableSelected().length;
    return count > 0
      ? this.translate.instant('execution.bulk.cancel_count', { count })
      : this.translate.instant('execution.bulk.cancel');
  });

  readonly getRowId = (params: any) => String(params.data.id);

  private readonly bulkApproveMutation = injectMutation(() => ({
    mutationFn: (actionIds: number[]) =>
      lastValueFrom(this.actionApi.bulkApprove(this.wsStore.currentWorkspaceId()!, { actionIds })),
    onSuccess: (result) => {
      this.showBulkApproveModal.set(false);
      this.selectedRows.set([]);
      this.invalidateAll();
      const failed = result.skipped + result.errored;
      if (failed === 0) {
        this.toast.success(this.translate.instant('execution.bulk.approve_success', { count: result.processed }));
      } else {
        this.toast.warning(this.translate.instant('execution.bulk.approve_partial_success', {
          processed: result.processed, total: result.processed + failed, failed,
        }));
      }
    },
    onError: (err) => {
      this.showBulkApproveModal.set(false);
      this.toast.error(translateApiErrorMessage(this.translate, err, 'execution.bulk.approve_error'));
    },
  }));

  readonly bulkCancelMutation = injectMutation(() => ({
    mutationFn: (req: { actionIds: number[]; cancelReason: string }) =>
      lastValueFrom(this.actionApi.bulkCancel(this.wsStore.currentWorkspaceId()!, req)),
    onSuccess: (result) => {
      this.showBulkCancelModal.set(false);
      this.bulkCancelReason.set('');
      this.selectedRows.set([]);
      this.invalidateAll();
      const failed = result.skipped + result.errored;
      if (failed === 0) {
        this.toast.success(this.translate.instant('execution.bulk.cancel_success', { count: result.processed }));
      } else {
        this.toast.warning(this.translate.instant('execution.bulk.cancel_partial_success', {
          processed: result.processed, total: result.processed + failed, failed,
        }));
      }
    },
    onError: (err) => {
      this.showBulkCancelModal.set(false);
      this.toast.error(translateApiErrorMessage(this.translate, err, 'execution.bulk.cancel_error'));
    },
  }));

  private readonly ctxApproveMutation = injectMutation(() => ({
    mutationFn: (id: number) =>
      lastValueFrom(this.actionApi.approveAction(this.wsStore.currentWorkspaceId()!, id)),
    onSuccess: () => { this.invalidateAll(); this.toast.success(this.translate.instant('execution.detail.toast.approved')); },
    onError: () => this.toast.error(this.translate.instant('execution.detail.action_error')),
  }));

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
    this.currentPage.set(0);
  }

  onRowClicked(row: ActionSummary): void {
    this.detailPanel.open('action', row.id);
  }

  onRowDoubleClicked(row: ActionSummary): void {
    this.detailPanel.close();
    this.router.navigate(['/workspace', this.wsStore.currentWorkspaceId(), 'execution', 'actions', row.id]);
  }

  onSelectionChanged(rows: any[]): void {
    this.selectedRows.set(rows);
  }

  onContextMenu(event: { event: MouseEvent; data: any }): void {
    this.ctxMenu.set({
      visible: true,
      x: event.event.clientX,
      y: event.event.clientY,
      row: event.data,
    });
  }

  openBulkApprove(): void {
    if (this.pendingSelected().length > 0) {
      this.showBulkApproveModal.set(true);
    } else {
      this.toast.warning(this.translate.instant('execution.bulk.no_eligible'));
    }
  }

  openBulkCancel(): void {
    if (this.cancellableSelected().length > 0) {
      this.bulkCancelReason.set('');
      this.showBulkCancelModal.set(true);
    } else {
      this.toast.warning(this.translate.instant('execution.bulk.no_cancellable'));
    }
  }

  executeBulkApprove(): void {
    const ids = this.pendingSelected().map((r) => r.id);
    if (ids.length > 0) this.bulkApproveMutation.mutate(ids);
  }

  executeBulkCancel(): void {
    const ids = this.cancellableSelected().map((r) => r.id);
    if (ids.length > 0) {
      this.bulkCancelMutation.mutate({ actionIds: ids, cancelReason: this.bulkCancelReason() });
    }
  }

  exportCsv(): void {
    this.exportPending.set(true);
    lastValueFrom(
      this.actionApi.exportActions(this.wsStore.currentWorkspaceId()!, this.filter()),
    ).then((blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `actions-${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
      this.exportPending.set(false);
      this.toast.success(this.translate.instant('execution.list.export_success'));
    }).catch(() => {
      this.exportPending.set(false);
      this.toast.error(this.translate.instant('execution.list.export_error'));
    });
  }

  clearSelection(): void {
    this.selectedRows.set([]);
  }

  canCancelRow(row: ActionSummary | null): boolean {
    return !!row && CANCELLABLE.has(row.status);
  }

  ctxOpenFullPage(): void {
    const row = this.ctxMenu().row;
    this.closeContextMenu();
    if (row) {
      this.router.navigate(['/workspace', this.wsStore.currentWorkspaceId(), 'execution', 'actions', row.id]);
    }
  }

  ctxApprove(): void {
    const row = this.ctxMenu().row;
    this.closeContextMenu();
    if (row) this.ctxApproveMutation.mutate(row.id);
  }

  ctxHold(): void {
    const row = this.ctxMenu().row;
    this.closeContextMenu();
    if (row) {
      this.detailPanel.open('action', row.id);
    }
  }

  ctxCancel(): void {
    const row = this.ctxMenu().row;
    this.closeContextMenu();
    if (row) {
      this.detailPanel.open('action', row.id);
    }
  }

  ctxCopySku(): void {
    const row = this.ctxMenu().row;
    this.closeContextMenu();
    if (row) {
      navigator.clipboard.writeText(row.sku).then(() => {
        this.toast.info(this.translate.instant('common.copied'));
      });
    }
  }

  private invalidateAll(): void {
    this.queryClient.invalidateQueries({ queryKey: ['actions'] });
    this.queryClient.invalidateQueries({ queryKey: ['actions-kpi'] });
  }

  private formatPrice(value: number | null): string {
    return formatMoney(value, 0);
  }

  private formatRelativeTime(iso: string | null): string {
    return formatRelativeTime(iso);
  }
}
