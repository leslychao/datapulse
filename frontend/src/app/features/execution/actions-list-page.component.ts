import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { ActionApiService } from '@core/api/action-api.service';
import { formatMoney, formatRelativeTime } from '@shared/utils/format.utils';
import { ActionFilter, ActionSummary } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { StatusColor } from '@shared/components/status-badge.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';

const ACTION_STATUS_COLOR: Record<string, StatusColor> = {
  PENDING_APPROVAL: 'info',
  APPROVED: 'info',
  ON_HOLD: 'warning',
  SCHEDULED: 'info',
  EXECUTING: 'warning',
  RECONCILIATION_PENDING: 'warning',
  RETRY_SCHEDULED: 'warning',
  SUCCEEDED: 'success',
  FAILED: 'error',
  EXPIRED: 'neutral',
  CANCELLED: 'neutral',
  SUPERSEDED: 'neutral',
};

const ACTION_STATUS_LABEL: Record<string, string> = {
  PENDING_APPROVAL: 'Ожидает',
  APPROVED: 'Одобрено',
  ON_HOLD: 'Приостановлено',
  SCHEDULED: 'Запланировано',
  EXECUTING: 'Выполняется',
  RECONCILIATION_PENDING: 'Проверка',
  RETRY_SCHEDULED: 'Повтор',
  SUCCEEDED: 'Выполнено',
  FAILED: 'Ошибка',
  EXPIRED: 'Истекло',
  CANCELLED: 'Отменено',
  SUPERSEDED: 'Заменено',
};

@Component({
  selector: 'dp-actions-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    KpiCardComponent,
    FilterBarComponent,
    DataGridComponent,
    EmptyStateComponent,
    ConfirmationModalComponent,
  ],
  template: `
    <div class="flex h-full flex-col">
      <!-- KPI Strip -->
      <div class="flex gap-3 bg-[var(--bg-secondary)] px-4 py-3">
        <dp-kpi-card
          [label]="'execution.kpi.total' | translate"
          [value]="kpiTotal()"
          [loading]="actionsQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'execution.kpi.pending' | translate"
          [value]="kpiPending()"
          [loading]="actionsQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'execution.kpi.executing' | translate"
          [value]="kpiExecuting()"
          [loading]="actionsQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'execution.kpi.failed' | translate"
          [value]="kpiFailed()"
          [loading]="actionsQuery.isPending()"
        />
      </div>

      <!-- Filter Bar -->
      <dp-filter-bar
        [filters]="filterConfigs"
        [values]="filterValues()"
        (filtersChanged)="onFiltersChanged($event)"
      />

      <!-- Data Grid -->
      <div class="flex-1 px-4 py-3">
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
            (rowClicked)="onRowClicked($event)"
            (selectionChanged)="onSelectionChanged($event)"
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
          <button
            (click)="openBulkApprove()"
            class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
          >
            {{ 'execution.bulk.approve_selected' | translate }}
          </button>
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
  `,
})
export class ActionsListPageComponent {
  private readonly actionApi = inject(ActionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);

  readonly filterValues = signal<Record<string, any>>({});
  readonly selectedRows = signal<ActionSummary[]>([]);
  readonly showBulkApproveModal = signal(false);
  readonly currentPage = signal(0);
  readonly currentSort = signal('createdAt,desc');

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: 'Статус',
      type: 'multi-select',
      options: Object.entries(ACTION_STATUS_LABEL).map(([value, label]) => ({ value, label })),
    },
    {
      key: 'executionMode',
      label: 'Режим',
      type: 'select',
      options: [
        { value: 'LIVE', label: 'LIVE' },
        { value: 'SIMULATED', label: 'Симуляция' },
      ],
    },
    { key: 'search', label: 'Поиск оффера', type: 'text' },
    { key: 'period', label: 'Период', type: 'date-range' },
  ];

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
      headerName: 'Оффер',
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
      headerName: 'Целевая цена',
      field: 'targetPrice',
      width: 110,
      sortable: true,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: any) => this.formatPrice(params.value),
    },
    {
      headerName: 'Текущая цена',
      field: 'currentPriceAtCreation',
      width: 110,
      sortable: true,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: any) => this.formatPrice(params.value),
    },
    {
      headerName: 'Δ цены',
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
      headerName: 'Статус',
      field: 'status',
      width: 140,
      sortable: true,
      cellRenderer: (params: any) => {
        const st = params.value as string;
        const label = ACTION_STATUS_LABEL[st] ?? st;
        const color = ACTION_STATUS_COLOR[st] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        return `<span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                  style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}">
          <span class="inline-block h-1.5 w-1.5 rounded-full" style="background-color: ${cssVar}"></span>
          ${label}
        </span>`;
      },
    },
    {
      headerName: 'Режим',
      field: 'executionMode',
      width: 80,
      sortable: true,
      cellRenderer: (params: any) => {
        if (params.value === 'SIMULATED') {
          return `<span class="rounded-full border border-dashed border-[var(--border-default)] px-2 py-0.5 text-[11px] text-[var(--text-secondary)]">SIM</span>`;
        }
        return `<span class="text-[var(--text-primary)] text-[11px]">LIVE</span>`;
      },
    },
    {
      headerName: 'Попытки',
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
      headerName: 'Создано',
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
  }));

  readonly rows = computed(() => this.actionsQuery.data()?.content ?? []);

  readonly hasActiveFilters = computed(() =>
    Object.values(this.filterValues()).some(
      (v) => v !== '' && v !== null && v !== undefined && (!Array.isArray(v) || v.length > 0),
    ),
  );

  readonly kpiTotal = computed(() => {
    const total = this.actionsQuery.data()?.totalElements;
    return total !== undefined ? total.toLocaleString('ru-RU') : null;
  });

  readonly kpiPending = computed(() => {
    const data = this.rows();
    return data.filter((r) => r.status === 'PENDING_APPROVAL').length || null;
  });

  readonly kpiExecuting = computed(() => {
    const data = this.rows();
    return data.filter((r) => r.status === 'EXECUTING').length || null;
  });

  readonly kpiFailed = computed(() => {
    const data = this.rows();
    return data.filter((r) => r.status === 'FAILED').length || null;
  });

  private readonly pendingSelected = computed(() =>
    this.selectedRows().filter((r) => r.status === 'PENDING_APPROVAL'),
  );

  readonly bulkApproveMessage = computed(() => {
    const total = this.selectedRows().length;
    const eligible = this.pendingSelected().length;
    const skipped = total - eligible;
    if (eligible === 0) {
      return 'Среди выбранных нет действий, ожидающих одобрения.';
    }
    let msg = `Одобрить ${eligible} действий?`;
    if (skipped > 0) {
      msg += `\nИз выбранных ${total} строк, ${eligible} находятся в статусе «Ожидает» и будут одобрены. Остальные ${skipped} будут пропущены.`;
    }
    return msg;
  });

  readonly bulkApproveLabel = computed(() => {
    const count = this.pendingSelected().length;
    return count > 0 ? `Одобрить ${count}` : 'Одобрить';
  });

  readonly getRowId = (params: any) => String(params.data.id);

  private readonly bulkApproveMutation = injectMutation(() => ({
    mutationFn: (actionIds: number[]) =>
      lastValueFrom(this.actionApi.bulkApprove(this.wsStore.currentWorkspaceId()!, { actionIds })),
    onSuccess: (result) => {
      this.showBulkApproveModal.set(false);
      this.selectedRows.set([]);
      this.queryClient.invalidateQueries({ queryKey: ['actions'] });
      const failed = result.skipped + result.errored;
      if (failed === 0) {
        this.toast.success(`Одобрено: ${result.processed} действий`);
      } else {
        this.toast.warning(
          `Одобрено: ${result.processed} из ${result.processed + failed}. ${failed} действий были изменены другим пользователем.`,
        );
      }
    },
    onError: () => {
      this.showBulkApproveModal.set(false);
      this.toast.error('Не удалось одобрить действия. Попробуйте позже.');
    },
  }));

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
    this.currentPage.set(0);
  }

  onRowClicked(row: ActionSummary): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'execution', 'actions', row.id]);
  }

  onSelectionChanged(rows: any[]): void {
    this.selectedRows.set(rows);
  }

  openBulkApprove(): void {
    if (this.pendingSelected().length > 0) {
      this.showBulkApproveModal.set(true);
    } else {
      this.toast.warning('Среди выбранных нет действий, ожидающих одобрения.');
    }
  }

  executeBulkApprove(): void {
    const ids = this.pendingSelected().map((r) => r.id);
    if (ids.length > 0) {
      this.bulkApproveMutation.mutate(ids);
    }
  }

  clearSelection(): void {
    this.selectedRows.set([]);
  }

  private formatPrice(value: number | null): string {
    return formatMoney(value, 0);
  }

  private formatRelativeTime(iso: string | null): string {
    return formatRelativeTime(iso);
  }
}
