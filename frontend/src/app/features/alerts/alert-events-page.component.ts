import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { AgGridAngular } from 'ag-grid-angular';
import {
  ColDef,
  ICellRendererParams,
  RowClickedEvent,
  SortChangedEvent,
} from 'ag-grid-community';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { AlertApiService } from '@core/api/alert-api.service';
import { ConnectionApiService } from '@core/api/connection-api.service';
import {
  AlertEvent,
  AlertFilter,
  AlertRuleType,
  AlertSeverity,
  AlertStatus,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { DetailPanelService } from '@shared/services/detail-panel.service';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { LucideAngularModule, AlertCircle, AlertTriangle, Eye, CheckCircle2 } from 'lucide-angular';
import { AG_GRID_LOCALE_RU } from '@shared/config/ag-grid-locale';
import { formatDateTime, renderBadge } from '@shared/utils/format.utils';

const RULE_TYPES: AlertRuleType[] = [
  'STALE_DATA',
  'MISSING_SYNC',
  'RESIDUAL_ANOMALY',
  'SPIKE_DETECTION',
  'MISMATCH',
  'ACTION_FAILED',
  'STUCK_STATE',
  'RECONCILIATION_FAILED',
  'POISON_PILL',
  'PROMO_MISMATCH',
  'ACTION_DEFERRED',
];

const RULE_ICON: Record<AlertRuleType, string> = {
  STALE_DATA: '⏱',
  MISSING_SYNC: '☁',
  RESIDUAL_ANOMALY: '◆',
  SPIKE_DETECTION: '📈',
  MISMATCH: '≠',
  ACTION_FAILED: '✕',
  STUCK_STATE: '⏸',
  RECONCILIATION_FAILED: '↻',
  POISON_PILL: '☠',
  PROMO_MISMATCH: '🎁',
  ACTION_DEFERRED: '⏳',
};

function buildAlertFilter(values: Record<string, unknown>): AlertFilter {
  const f: AlertFilter = {};
  const rt = values['ruleType'];
  if (Array.isArray(rt) && rt.length > 0) {
    f.ruleType = rt as AlertRuleType[];
  }
  const sev = values['severity'];
  if (Array.isArray(sev) && sev.length > 0) {
    f.severity = sev as AlertSeverity[];
  }
  const st = values['status'];
  if (Array.isArray(st) && st.length > 0) {
    f.status = st as AlertStatus[];
  }
  const cid = values['connectionId'];
  if (typeof cid === 'string' && cid !== '') {
    const n = Number(cid);
    if (!Number.isNaN(n)) {
      f.connectionId = n;
    }
  }
  return f;
}

@Component({
  selector: 'dp-alert-events-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    AgGridAngular,
    FilterBarComponent,
    KpiCardComponent,
    EmptyStateComponent,
    LucideAngularModule,
  ],
  templateUrl: './alert-events-page.component.html',
})
export class AlertEventsPageComponent {
  private readonly alertApi = inject(AlertApiService);
  private readonly connectionApi = inject(ConnectionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly detailPanel = inject(DetailPanelService);
  private readonly translate = inject(TranslateService);

  readonly eventId = input<string | undefined>();

  readonly filterValues = signal<Record<string, string | string[] | object>>({
    status: ['OPEN', 'ACKNOWLEDGED'],
  });

  readonly pageIndex = signal(0);
  readonly pageSize = signal(25);
  readonly sortField = signal('openedAt,desc');

  readonly alertCircleIcon = AlertCircle;
  readonly alertTriangleIcon = AlertTriangle;
  readonly eyeIcon = Eye;
  readonly checkCircleIcon = CheckCircle2;

  readonly workspaceId = computed(() => this.wsStore.currentWorkspaceId());

  readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections', this.workspaceId()],
    queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
    enabled: this.workspaceId() != null,
  }));

  readonly summaryQuery = injectQuery(() => ({
    queryKey: ['alerts', 'summary', this.workspaceId()],
    queryFn: () => lastValueFrom(this.alertApi.getSummary()),
    enabled: this.workspaceId() != null,
    staleTime: 30_000,
  }));

  readonly alertsQuery = injectQuery(() => ({
    queryKey: [
      'alerts',
      'list',
      this.workspaceId(),
      this.filterValues(),
      this.pageIndex(),
      this.pageSize(),
      this.sortField(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.alertApi.listAlerts(
          buildAlertFilter(this.filterValues()),
          this.pageIndex(),
          this.pageSize(),
          this.sortField(),
        ),
      ),
    enabled: this.workspaceId() != null,
  }));

  readonly filterConfigs = computed<FilterConfig[]>(() => {
    const connOpts =
      this.connectionsQuery.data()?.map((c) => ({
        value: String(c.id),
        label: c.name,
      })) ?? [];
    return [
      {
        key: 'ruleType',
        label: 'alerts.filter.rule_type',
        type: 'multi-select',
        options: RULE_TYPES.map((value) => ({
          value,
          label: `alerts.rule_type.${value}`,
        })),
      },
      {
        key: 'severity',
        label: 'alerts.filter.severity',
        type: 'multi-select',
        options: [
          { value: 'CRITICAL', label: 'alerts.severity.CRITICAL' },
          { value: 'WARNING', label: 'alerts.severity.WARNING' },
          { value: 'INFO', label: 'alerts.severity.INFO' },
        ],
      },
      {
        key: 'connectionId',
        label: 'alerts.filter.connection',
        type: 'select',
        options: [
          { value: '', label: 'alerts.filter.all_connections' },
          ...connOpts,
        ],
      },
      {
        key: 'status',
        label: 'alerts.filter.status',
        type: 'multi-select',
        options: [
          { value: 'OPEN', label: 'alerts.status.OPEN' },
          { value: 'ACKNOWLEDGED', label: 'alerts.status.ACKNOWLEDGED' },
          { value: 'RESOLVED', label: 'alerts.status.RESOLVED' },
          { value: 'AUTO_RESOLVED', label: 'alerts.status.AUTO_RESOLVED' },
        ],
      },
    ];
  });

  readonly columnDefs: ColDef<AlertEvent>[] = [
    {
      colId: 'ruleTypeDisplay',
      field: 'ruleType',
      headerValueGetter: () => this.translate.instant('alerts.col.rule_type'),
      flex: 1,
      minWidth: 180,
      cellRenderer: (params: ICellRendererParams<AlertEvent>) => {
        const wrap = document.createElement('div');
        wrap.className = 'flex items-center gap-2';
        const t = params.data?.ruleType;
        const icon = document.createElement('span');
        icon.className = 'text-base leading-none';
        icon.textContent = t ? RULE_ICON[t] ?? '•' : '•';
        icon.title = t ?? '';
        const label = document.createElement('span');
        label.className = 'text-[length:var(--text-sm)] text-[var(--text-primary)]';
        label.textContent = t
          ? this.translate.instant(`alerts.rule_type.${t}`)
          : '';
        wrap.appendChild(icon);
        wrap.appendChild(label);
        return wrap;
      },
    },
    {
      field: 'severity',
      headerValueGetter: () => this.translate.instant('alerts.col.severity'),
      width: 120,
      cellRenderer: (params: ICellRendererParams<AlertEvent>) => {
        const sev = params.data?.severity;
        if (!sev) {
          return '';
        }
        const fg =
          sev === 'CRITICAL'
            ? 'var(--status-error)'
            : sev === 'WARNING'
              ? 'var(--status-warning)'
              : 'var(--status-info)';
        const label = this.translate.instant(`alerts.severity.${sev}`);
        return renderBadge(label, fg);
      },
    },
    {
      field: 'connectionName',
      headerValueGetter: () => this.translate.instant('alerts.col.connection'),
      flex: 1,
      minWidth: 120,
      valueFormatter: (p) => p.value ?? '—',
    },
    {
      field: 'status',
      headerValueGetter: () => this.translate.instant('alerts.col.status'),
      width: 140,
      cellRenderer: (params: ICellRendererParams<AlertEvent>) => {
        const st = params.data?.status;
        if (!st) {
          return '';
        }
        const label = this.translate.instant(`alerts.status.${st}`);
        return renderBadge(label, 'var(--text-primary)');
      },
    },
    {
      field: 'title',
      headerValueGetter: () => this.translate.instant('alerts.col.title'),
      flex: 2,
      minWidth: 160,
    },
    {
      field: 'openedAt',
      headerValueGetter: () => this.translate.instant('alerts.col.opened_at'),
      width: 150,
      valueFormatter: (p) => formatDateTime(p.value as string),
    },
    {
      field: 'resolvedAt',
      headerValueGetter: () => this.translate.instant('alerts.col.resolved_at'),
      width: 150,
      valueFormatter: (p) => formatDateTime(p.value as string | null),
    },
  ];

  readonly localeText = AG_GRID_LOCALE_RU;

  readonly getRowId = (params: { data?: AlertEvent }) =>
    params.data ? String(params.data.id) : '';

  readonly rows = computed(() => this.alertsQuery.data()?.content ?? []);

  readonly totalPages = computed(() => this.alertsQuery.data()?.totalPages ?? 0);

  readonly totalElements = computed(() => this.alertsQuery.data()?.totalElements ?? 0);

  readonly hasActiveFilters = computed(() => {
    const v = this.filterValues();
    const def = JSON.stringify({ status: ['OPEN', 'ACKNOWLEDGED'] });
    return JSON.stringify(v) !== def;
  });

  constructor() {
    effect(() => {
      const paramId = this.eventId();
      const ws = this.workspaceId();
      if (!ws) {
        return;
      }
      if (paramId) {
        const n = Number(paramId);
        if (!Number.isNaN(n)) {
          this.detailPanel.open('alert', n);
        }
      }
    });

    effect(() => {
      const paramId = this.eventId();
      if (!paramId && this.detailPanel.entityType() === 'alert') {
        this.detailPanel.close();
      }
    });
  }

  onFiltersChanged(next: Record<string, unknown>): void {
    this.filterValues.set(next as Record<string, string | string[] | object>);
    this.pageIndex.set(0);
  }

  onKpiClick(kind: 'critical' | 'warning' | 'ack' | 'resolved'): void {
    const base: Record<string, string[]> = { status: ['OPEN', 'ACKNOWLEDGED'] };
    switch (kind) {
      case 'critical':
        this.filterValues.set({ ...base, severity: ['CRITICAL'] });
        break;
      case 'warning':
        this.filterValues.set({ ...base, severity: ['WARNING'] });
        break;
      case 'ack':
        this.filterValues.set({ status: ['ACKNOWLEDGED'] });
        break;
      case 'resolved':
        this.filterValues.set({ status: ['RESOLVED', 'AUTO_RESOLVED'] });
        break;
    }
    this.pageIndex.set(0);
  }

  onSortChanged(event: SortChangedEvent): void {
    const col = event.api.getColumnState()?.find((c) => c.sort);
    if (!col?.colId || !col.sort) {
      return;
    }
    const dir = col.sort === 'asc' ? 'asc' : 'desc';
    this.sortField.set(`${col.colId},${dir}`);
    this.pageIndex.set(0);
  }

  onRowClicked(event: RowClickedEvent<AlertEvent>): void {
    const row = event.data;
    if (!row) {
      return;
    }
    const ws = this.workspaceId();
    if (ws == null) {
      return;
    }
    this.router.navigate(['/workspace', ws, 'alerts', 'events', row.id]);
  }

  /** Сброс к умолчанию: открытые и подтверждённые (как при первом открытии страницы). */
  resetFilters(): void {
    this.filterValues.set({ status: ['OPEN', 'ACKNOWLEDGED'] });
    this.pageIndex.set(0);
  }

  prevPage(): void {
    const p = this.pageIndex();
    if (p > 0) {
      this.pageIndex.set(p - 1);
    }
  }

  nextPage(): void {
    const p = this.pageIndex();
    const tp = this.totalPages();
    if (p + 1 < tp) {
      this.pageIndex.set(p + 1);
    }
  }
}
