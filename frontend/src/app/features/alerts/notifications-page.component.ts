import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AgGridAngular } from 'ag-grid-angular';
import {
  ColDef,
  GridApi,
  GridReadyEvent,
  ICellRendererParams,
  SelectionChangedEvent,
} from 'ag-grid-community';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, Bell } from 'lucide-angular';

import { NotificationApiService } from '@core/api/notification-api.service';
import { AppNotification, NotificationType } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { AG_GRID_LOCALE_RU } from '@shared/config/ag-grid-locale';
import { formatDateTime } from '@shared/utils/format.utils';

const NOTIFICATION_TYPES: NotificationType[] = [
  'ALERT',
  'APPROVAL_REQUEST',
  'SYNC_COMPLETED',
  'ACTION_FAILED',
];

@Component({
  selector: 'dp-notifications-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    AgGridAngular,
    FilterBarComponent,
    EmptyStateComponent,
    LucideAngularModule,
  ],
  templateUrl: './notifications-page.component.html',
})
export class NotificationsPageComponent {
  private readonly notificationApi = inject(NotificationApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);

  readonly bellIcon = Bell;

  readonly filterValues = signal<Record<string, string | string[] | object>>({
    period: '7d',
  });

  readonly pageIndex = signal(0);
  readonly pageSize = signal(25);

  readonly expandedIds = signal<Set<number>>(new Set());
  private gridApi: GridApi<AppNotification> | null = null;

  readonly selectedRows = signal<AppNotification[]>([]);

  readonly workspaceId = computed(() => this.wsStore.currentWorkspaceId());

  readonly sinceIso = computed(() => {
    const period = this.filterValues()['period'];
    const p = typeof period === 'string' ? period : '7d';
    if (p === 'all') {
      return undefined;
    }
    const now = Date.now();
    const ms =
      p === '24h'
        ? 24 * 60 * 60 * 1000
        : p === '7d'
          ? 7 * 24 * 60 * 60 * 1000
          : p === '30d'
            ? 30 * 24 * 60 * 60 * 1000
            : 7 * 24 * 60 * 60 * 1000;
    return new Date(now - ms).toISOString();
  });

  readonly notificationsQuery = injectQuery(() => ({
    queryKey: ['notifications', 'list', this.workspaceId(), this.sinceIso()],
    queryFn: () =>
      lastValueFrom(
        this.notificationApi.list({
          size: 500,
          since: this.sinceIso(),
        }),
      ),
    enabled: this.workspaceId() != null,
  }));

  readonly markAllReadMutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.notificationApi.markAllRead()),
    onSuccess: () => {
      this.toast.success(this.translate.instant('alerts.notifications.mark_all_success'));
      this.queryClient.invalidateQueries({ queryKey: ['notifications'] });
      this.selectedRows.set([]);
      this.gridApi?.deselectAll();
    },
    onError: () => this.toast.error(this.translate.instant('alerts.notifications.mark_all_error')),
  }));

  readonly filterConfigs = computed<FilterConfig[]>(() => [
    {
      key: 'severity',
      label: this.translate.instant('alerts.filter.severity'),
      type: 'multi-select',
      options: [
        { value: 'CRITICAL', label: this.translate.instant('alerts.severity.CRITICAL') },
        { value: 'WARNING', label: this.translate.instant('alerts.severity.WARNING') },
        { value: 'INFO', label: this.translate.instant('alerts.severity.INFO') },
      ],
    },
    {
      key: 'read',
      label: this.translate.instant('alerts.notifications.filter_read'),
      type: 'select',
      options: [
        { value: '', label: this.translate.instant('alerts.notifications.read_all') },
        { value: 'unread', label: this.translate.instant('alerts.notifications.read_unread') },
        { value: 'read', label: this.translate.instant('alerts.notifications.read_read') },
      ],
    },
    {
      key: 'period',
      label: this.translate.instant('alerts.notifications.filter_period'),
      type: 'select',
      options: [
        { value: '24h', label: this.translate.instant('alerts.notifications.period_24h') },
        { value: '7d', label: this.translate.instant('alerts.notifications.period_7d') },
        { value: '30d', label: this.translate.instant('alerts.notifications.period_30d') },
        { value: 'all', label: this.translate.instant('alerts.notifications.period_all') },
      ],
    },
    {
      key: 'notificationType',
      label: this.translate.instant('alerts.notifications.filter_type'),
      type: 'multi-select',
      options: NOTIFICATION_TYPES.map((value) => ({
        value,
        label: this.translate.instant(`alerts.notification_type.${value}`),
      })),
    },
  ]);

  readonly filteredRows = computed(() => {
    const raw = this.notificationsQuery.data() ?? [];
    const v = this.filterValues();
    const sev = v['severity'];
    const read = typeof v['read'] === 'string' ? v['read'] : '';
    const types = v['notificationType'];

    return raw.filter((n) => {
      if (Array.isArray(sev) && sev.length > 0 && !sev.includes(n.severity)) {
        return false;
      }
      if (read === 'read' && !n.read) {
        return false;
      }
      if (read === 'unread' && n.read) {
        return false;
      }
      if (Array.isArray(types) && types.length > 0 && !types.includes(n.notificationType)) {
        return false;
      }
      return true;
    });
  });

  readonly pagedRows = computed(() => {
    const list = this.filteredRows();
    const start = this.pageIndex() * this.pageSize();
    return list.slice(start, start + this.pageSize());
  });

  readonly totalFiltered = computed(() => this.filteredRows().length);

  readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.totalFiltered() / this.pageSize())),
  );

  readonly hasNoServerRows = computed(
    () => (this.notificationsQuery.data()?.length ?? 0) === 0,
  );

  readonly columnDefs: ColDef<AppNotification>[] = this.buildColumnDefs();

  readonly localeText = AG_GRID_LOCALE_RU;

  readonly getRowId = (params: { data?: AppNotification }) =>
    params.data ? String(params.data.id) : '';

  onFiltersChanged(next: Record<string, unknown>): void {
    this.filterValues.set(next as Record<string, string | string[] | object>);
    this.pageIndex.set(0);
  }

  onGridReady(event: GridReadyEvent<AppNotification>): void {
    this.gridApi = event.api;
  }

  onSelectionChanged(event: SelectionChangedEvent<AppNotification>): void {
    const rows = event.api.getSelectedRows();
    this.selectedRows.set(rows);
  }

  toggleBodyExpand(id: number): void {
    const next = new Set(this.expandedIds());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.expandedIds.set(next);
    this.gridApi?.refreshCells({ force: true, columns: ['body'] });
  }

  bulkMarkRead(): void {
    const ids = this.selectedRows().filter((r) => !r.read).map((r) => r.id);
    if (ids.length === 0) {
      return;
    }
    Promise.all(ids.map((id) => lastValueFrom(this.notificationApi.markRead(id))))
      .then(() => {
        this.queryClient.invalidateQueries({ queryKey: ['notifications'] });
        this.toast.success(this.translate.instant('alerts.notifications.bulk_read_success'));
        this.selectedRows.set([]);
        this.gridApi?.deselectAll();
      })
      .catch(() => this.toast.error(this.translate.instant('alerts.notifications.mark_read_error')));
  }

  markAllRead(): void {
    this.markAllReadMutation.mutate();
  }

  prevPage(): void {
    const p = this.pageIndex();
    if (p > 0) {
      this.pageIndex.set(p - 1);
    }
  }

  nextPage(): void {
    const p = this.pageIndex();
    if (p + 1 < this.totalPages()) {
      this.pageIndex.set(p + 1);
    }
  }

  private buildColumnDefs(): ColDef<AppNotification>[] {
    return [
      {
        width: 48,
        checkboxSelection: true,
        headerCheckboxSelection: true,
        pinned: 'left' as const,
        sortable: false,
        resizable: false,
      },
      {
        field: 'severity',
        headerValueGetter: () => this.translate.instant('alerts.col.severity'),
        width: 56,
        sortable: false,
        cellRenderer: (params: ICellRendererParams<AppNotification>) => {
          const dot = document.createElement('span');
          const sev = params.data?.severity;
          if (!sev) {
            return dot;
          }
          dot.className = 'inline-block h-2 w-2 rounded-full';
          dot.style.backgroundColor =
            sev === 'CRITICAL'
              ? 'var(--status-error)'
              : sev === 'WARNING'
                ? 'var(--status-warning)'
                : 'var(--status-info)';
          return dot;
        },
      },
      {
        field: 'title',
        headerValueGetter: () => this.translate.instant('alerts.notifications.col.title'),
        flex: 1,
        minWidth: 140,
      },
      {
        colId: 'body',
        field: 'body',
        headerValueGetter: () => this.translate.instant('alerts.notifications.col.body'),
        flex: 2,
        minWidth: 200,
        wrapText: true,
        autoHeight: true,
        cellRenderer: (params: ICellRendererParams<AppNotification>) => {
          const wrap = document.createElement('div');
          const id = params.data?.id;
          const body = params.data?.body ?? '';
          if (id == null) {
            wrap.textContent = body;
            return wrap;
          }
          const expanded = this.expandedIds().has(id);
          const text = document.createElement('div');
          text.className = 'text-[length:var(--text-sm)] text-[var(--text-primary)]';
          text.style.whiteSpace = expanded ? 'pre-wrap' : 'normal';
          text.textContent =
            expanded || body.length <= 160 ? body : `${body.slice(0, 160)}…`;
          wrap.appendChild(text);
          if (body.length > 160) {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className =
              'mt-1 cursor-pointer text-[length:var(--text-xs)] text-[var(--accent-primary)]';
            btn.textContent = expanded
              ? this.translate.instant('alerts.notifications.body_collapse')
              : this.translate.instant('alerts.notifications.body_expand');
            btn.addEventListener('click', (e) => {
              e.stopPropagation();
              this.toggleBodyExpand(id);
            });
            wrap.appendChild(btn);
          }
          return wrap;
        },
      },
      {
        field: 'notificationType',
        headerValueGetter: () => this.translate.instant('alerts.notifications.col.type'),
        width: 160,
        valueFormatter: (p) =>
          p.value
            ? this.translate.instant(`alerts.notification_type.${p.value as NotificationType}`)
            : '',
      },
      {
        colId: 'source',
        headerValueGetter: () => this.translate.instant('alerts.notifications.col.source'),
        width: 120,
        valueGetter: (p) => p.data?.alertEventId,
        valueFormatter: (p) => (p.value != null ? String(p.value) : '—'),
      },
      {
        field: 'createdAt',
        headerValueGetter: () => this.translate.instant('alerts.notifications.col.date'),
        width: 150,
        valueFormatter: (p) => formatDateTime(p.value as string),
      },
      {
        field: 'read',
        headerValueGetter: () => this.translate.instant('alerts.notifications.col.read'),
        width: 120,
        cellRenderer: (params: ICellRendererParams<AppNotification>) => {
          const span = document.createElement('span');
          const read = params.data?.read;
          span.className = 'text-[length:var(--text-sm)]';
          span.style.color = read ? 'var(--text-tertiary)' : 'var(--accent-primary)';
          span.textContent = read
            ? this.translate.instant('alerts.notifications.read_status_read')
            : this.translate.instant('alerts.notifications.read_status_unread');
          return span;
        },
      },
    ];
  }
}
