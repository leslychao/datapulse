import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  HostListener,
  inject,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AgGridAngular } from 'ag-grid-angular';
import {
  CellClickedEvent,
  CellContextMenuEvent,
  ColDef,
  GetRowIdParams,
  GridApi,
  GridReadyEvent,
  ICellRendererParams,
  RowClassParams,
  SelectionChangedEvent,
  SortChangedEvent,
  ValueFormatterParams,
} from 'ag-grid-community';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';
import { formatDistanceToNow } from 'date-fns';
import { ru } from 'date-fns/locale';
import type { EChartsOption } from 'echarts';

import { LucideAngularModule, Activity, AlertTriangle, Clock, CheckCircle } from 'lucide-angular';

import { MismatchApiService } from '@core/api/mismatch-api.service';
import { ConnectionApiService } from '@core/api/connection-api.service';
import {
  Mismatch,
  MismatchFilter,
  MismatchSeverity,
  MismatchStatus,
  MismatchType,
  MismatchWsEvent,
} from '@core/models';
import { WebSocketService } from '@core/websocket/websocket.service';
import { RbacService } from '@core/auth/rbac.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ViewStateService } from '@shared/services/view-state.service';
import { ToastService } from '@shared/shell/toast/toast.service';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { renderBadge } from '@shared/utils/format.utils';
import { PaginationBarComponent } from '@shared/components/pagination-bar/pagination-bar.component';
import { MismatchDetailPanelComponent } from './mismatch-detail-panel.component';

const DEFAULT_PAGE_SIZE = 50;

const STATUS_BADGE: Record<string, 'success' | 'error' | 'warning' | 'info' | 'neutral'> = {
  ACTIVE: 'error',
  ACKNOWLEDGED: 'warning',
  RESOLVED: 'success',
  AUTO_RESOLVED: 'success',
  IGNORED: 'neutral',
};

const TYPE_COLORS: Record<MismatchType, { bg: string; text: string }> = {
  PRICE: { bg: 'bg-blue-100', text: 'text-blue-700' },
  STOCK: { bg: 'bg-amber-100', text: 'text-amber-700' },
  PROMO: { bg: 'bg-purple-100', text: 'text-purple-700' },
  FINANCE: { bg: 'bg-indigo-100', text: 'text-indigo-700' },
};

@Component({
  selector: 'dp-mismatch-dashboard-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    AgGridAngular,
    LucideAngularModule,
    KpiCardComponent,
    EmptyStateComponent,
    ChartComponent,
    MismatchDetailPanelComponent,
    PaginationBarComponent,
  ],
  templateUrl: './mismatch-dashboard-page.component.html',
})
export class MismatchDashboardPageComponent implements OnInit {
  private readonly api = inject(MismatchApiService);
  private readonly connectionApi = inject(ConnectionApiService);
  private readonly ws = inject(WorkspaceContextStore);
  private readonly viewState = inject(ViewStateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);
  protected readonly rbac = inject(RbacService);
  private readonly webSocket = inject(WebSocketService);

  protected readonly ActivityIcon = Activity;
  protected readonly AlertTriangleIcon = AlertTriangle;
  protected readonly ClockIcon = Clock;
  protected readonly CheckCircleIcon = CheckCircle;

  private gridApi: GridApi<Mismatch> | null = null;
  private readonly searchSubject = new Subject<string>();

  readonly selectedFromQuery = toSignal(
    this.route.queryParamMap.pipe(map((m) => m.get('selected'))),
    { initialValue: null },
  );

  readonly selectedMismatchId = computed(() => {
    const s = this.selectedFromQuery();
    if (!s) return null;
    const n = Number(s);
    return Number.isFinite(n) ? n : null;
  });

  readonly connectionId = signal<number | null>(null);
  readonly filterTypes = signal<MismatchType[]>([]);
  readonly filterStatuses = signal<MismatchStatus[]>(['ACTIVE']);
  readonly filterSeverities = signal<MismatchSeverity[]>([]);
  readonly periodFrom = signal('');
  readonly periodTo = signal('');
  protected readonly today = new Date().toISOString().slice(0, 10);
  readonly searchQuery = signal('');

  readonly currentPage = signal(0);
  readonly pageSize = signal<number>(DEFAULT_PAGE_SIZE);
  readonly sortParam = signal('detectedAt,desc');

  readonly selectedRows = signal<Mismatch[]>([]);

  readonly showBulkIgnoreModal = signal(false);
  readonly bulkIgnoreReason = signal('');
  readonly showColumnsPanel = signal(false);

  readonly contextMenu = signal<{ x: number; y: number; row: Mismatch } | null>(null);

  @ViewChild('searchInput') searchInputRef?: ElementRef<HTMLInputElement>;

  readonly typeOptions: MismatchType[] = ['PRICE', 'STOCK', 'PROMO', 'FINANCE'];
  readonly statusOptions: MismatchStatus[] = [
    'ACTIVE',
    'ACKNOWLEDGED',
    'RESOLVED',
    'AUTO_RESOLVED',
    'IGNORED',
  ];
  readonly severityOptions: MismatchSeverity[] = ['WARNING', 'CRITICAL'];

  readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections'],
    queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
  }));

  readonly summaryQuery = injectQuery(() => ({
    queryKey: ['mismatch-summary', this.ws.currentWorkspaceId()],
    queryFn: () =>
      lastValueFrom(this.api.getSummary(this.ws.currentWorkspaceId()!)),
    enabled: !!this.ws.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly mismatchFilter = computed((): MismatchFilter => {
    const f: MismatchFilter = {};
    const cid = this.connectionId();
    if (cid != null) f.connectionId = [cid];
    const types = this.filterTypes();
    if (types.length) f.type = types;
    const st = this.filterStatuses();
    if (st.length) f.status = st;
    const sev = this.filterSeverities();
    if (sev.length) f.severity = sev;
    const from = this.periodFrom().trim();
    const to = this.periodTo().trim();
    if (from) f.from = from;
    if (to) f.to = to;
    const q = this.searchQuery().trim();
    if (q) f.query = q;
    return f;
  });

  readonly listQuery = injectQuery(() => ({
    queryKey: [
      'mismatches',
      this.ws.currentWorkspaceId(),
      this.mismatchFilter(),
      this.currentPage(),
      this.pageSize(),
      this.sortParam(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.api.list(
          this.ws.currentWorkspaceId()!,
          this.mismatchFilter(),
          this.currentPage(),
          this.pageSize(),
          this.sortParam(),
        ),
      ),
    enabled: !!this.ws.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.listQuery.data()?.content ?? []);
  readonly totalElements = computed(() => this.listQuery.data()?.totalElements ?? 0);

  readonly paginationLabel = computed(() => {
    const page = this.currentPage();
    const size = this.pageSize();
    const total = this.totalElements();
    const from = page * size + 1;
    const to = Math.min((page + 1) * size, total);
    return total > 0 ? `${from}\u2013${to}` : '0';
  });

  readonly avgHoursDisplay = computed(() => {
    const v = this.summaryQuery.data()?.avgHoursUnresolved;
    if (v == null) return null;
    return v.toFixed(1).replace('.', ',');
  });

  readonly hasActiveFilters = computed(
    () =>
      this.connectionId() != null ||
      this.filterTypes().length > 0 ||
      JSON.stringify(this.filterStatuses()) !== JSON.stringify(['ACTIVE']) ||
      this.filterSeverities().length > 0 ||
      this.periodFrom().trim() !== '' ||
      this.periodTo().trim() !== '' ||
      this.searchQuery().trim() !== '',
  );

  readonly donutOptions = computed((): EChartsOption => {
    const dist = this.summaryQuery.data()?.distributionByType ?? [];
    const colorMap: Record<string, string> = {
      PRICE: '#2563EB', STOCK: '#D97706', PROMO: '#7C3AED', FINANCE: '#4338CA',
    };
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', right: 10, top: 'center', textStyle: { fontSize: 12 } },
      series: [{
        type: 'pie',
        radius: ['45%', '75%'],
        avoidLabelOverlap: false,
        label: { show: false },
        emphasis: { label: { show: false } },
        data: dist.map(d => ({
          value: d.count,
          name: this.translate.instant('mismatches.type.' + d.type),
          itemStyle: { color: colorMap[d.type] ?? '#6B7280' },
        })),
      }],
    };
  });

  readonly timelineOptions = computed((): EChartsOption => {
    const tl = this.summaryQuery.data()?.timeline ?? [];
    return {
      tooltip: { trigger: 'axis' },
      legend: {
        data: [
          this.translate.instant('mismatches.charts.new'),
          this.translate.instant('mismatches.charts.resolved'),
        ],
        bottom: 0,
        textStyle: { fontSize: 11 },
      },
      grid: { left: 40, right: 16, top: 8, bottom: 32 },
      xAxis: {
        type: 'category',
        data: tl.map(d => d.date.slice(5).replace('-', '.')),
        axisLabel: { fontSize: 10 },
      },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          name: this.translate.instant('mismatches.charts.new'),
          type: 'bar',
          stack: 'a',
          data: tl.map(d => d.newCount),
          itemStyle: { color: 'rgba(220,38,38,0.5)' },
        },
        {
          name: this.translate.instant('mismatches.charts.resolved'),
          type: 'bar',
          stack: 'a',
          data: tl.map(d => d.resolvedCount),
          itemStyle: { color: 'rgba(5,150,105,0.5)' },
        },
      ],
    };
  });

  readonly toggleableColumns = computed(() =>
    this.columnDefs
      .filter(c => c.colId && c.colId !== 'selection')
      .map(c => ({
        colId: c.colId!,
        headerName: c.headerName ?? c.colId ?? '',
        visible: !c.hide,
      })),
  );

  readonly rowSelectionConfig = {
    mode: 'multiRow' as const,
    checkboxes: true,
    headerCheckbox: true,
    enableClickSelection: false,
  };

  readonly columnDefs!: ColDef<Mismatch>[];
  readonly defaultColDef: ColDef<Mismatch> = {
    resizable: true,
    suppressHeaderMenuButton: true,
  };

  readonly getRowId = (params: GetRowIdParams<Mismatch>) =>
    String(params.data?.mismatchId ?? '');

  readonly getRowClass = (params: RowClassParams<Mismatch>): string | undefined => {
    if (params.data?.severity === 'CRITICAL') {
      return 'dp-critical-row';
    }
    return undefined;
  };

  readonly bulkAckMutation = injectMutation(() => ({
    mutationFn: async (ids: number[]) => {
      const wsId = this.ws.currentWorkspaceId()!;
      for (const id of ids) {
        await lastValueFrom(this.api.acknowledge(wsId, id));
      }
    },
    onSuccess: () => {
      this.toast.success(this.translate.instant('mismatches.toast.bulk_ack'));
      this.clearGridSelection();
      this.invalidateList();
    },
    onError: () => this.toast.error(this.translate.instant('mismatches.toast.error')),
  }));

  readonly bulkIgnoreMutation = injectMutation(() => ({
    mutationFn: (payload: { ids: number[]; reason: string }) =>
      lastValueFrom(this.api.bulkIgnore(this.ws.currentWorkspaceId()!, payload.ids, payload.reason)),
    onSuccess: (_: void, vars: { ids: number[]; reason: string }) => {
      this.toast.success(this.translate.instant('mismatches.toast.bulk_ignore', { count: vars.ids.length }));
      this.showBulkIgnoreModal.set(false);
      this.bulkIgnoreReason.set('');
      this.clearGridSelection();
      this.invalidateList();
    },
    onError: () => this.toast.error(this.translate.instant('mismatches.toast.error')),
  }));

  private wsToastTimestamps: number[] = [];
  private readonly WS_TOAST_LIMIT = 3;
  private readonly WS_TOAST_WINDOW_MS = 10_000;

  constructor() {
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntilDestroyed(),
    ).subscribe(q => {
      this.searchQuery.set(q);
      this.applyFilters();
    });

    effect(() => {
      const evt = this.webSocket.lastMismatchEvent();
      if (!evt) return;
      this.applyPulseAnimation(evt);
      if (evt.eventType === 'MISMATCH_DETECTED' && evt.severity === 'CRITICAL') {
        this.showThrottledCriticalToast(evt);
      }
    });

    const tr = this.translate;
    this.columnDefs = [
      {
        headerName: tr.instant('mismatches.grid.offer'),
        colId: 'offerName',
        field: 'offerName',
        minWidth: 240,
        pinned: 'left' as const,
        sortable: true,
        tooltipValueGetter: (p: any) => p.data?.offerName ?? '',
        cellRenderer: (p: ICellRendererParams<Mismatch>) => {
          const d = p.data;
          if (!d) return '';
          return `<div class="leading-tight py-1"><div class="font-medium text-[var(--accent-primary)] cursor-pointer hover:underline">${escapeHtml(d.offerName)}</div><div class="font-mono text-[11px] text-[var(--text-secondary)]">${escapeHtml(d.skuCode)}</div></div>`;
        },
        onCellClicked: (params: CellClickedEvent<Mismatch>) => {
          if (params.data) {
            this.navigateToMismatchDetail(params.data);
          }
        },
      },
      {
        headerName: 'MP',
        headerTooltip: 'Marketplace',
        colId: 'marketplaceType',
        field: 'marketplaceType',
        width: 80,
        sortable: false,
        cellRenderer: (p: ICellRendererParams<Mismatch>) => {
          const t = p.data?.marketplaceType;
          if (t === 'OZON') {
            return '<span class="rounded px-1.5 py-0.5 text-[11px] font-semibold" style="background:#005BFF;color:#fff">Ozon</span>';
          }
          return '<span class="rounded px-1.5 py-0.5 text-[11px] font-semibold" style="background:#7B2FBE;color:#fff">WB</span>';
        },
      },
      {
        headerName: tr.instant('mismatches.grid.type'),
        headerTooltip: tr.instant('mismatches.grid.type'),
        colId: 'type',
        field: 'type',
        width: 100,
        sortable: true,
        cellRenderer: (p: ICellRendererParams<Mismatch>) => {
          const v = p.data?.type;
          if (!v) return '';
          const c = TYPE_COLORS[v] ?? TYPE_COLORS.PRICE;
          const label = tr.instant(`mismatches.type.${v}`);
          return `<span class="inline-block rounded-full px-2 py-0.5 text-[11px] font-medium ${c.bg} ${c.text}">${escapeHtml(label)}</span>`;
        },
      },
      {
        headerName: tr.instant('mismatches.grid.expected'),
        headerTooltip: tr.instant('mismatches.grid.expected'),
        colId: 'expectedValue',
        field: 'expectedValue',
        width: 120,
        sortable: true,
        cellClass: 'font-mono text-xs',
        type: 'rightAligned',
      },
      {
        headerName: tr.instant('mismatches.grid.actual'),
        headerTooltip: tr.instant('mismatches.grid.actual'),
        colId: 'actualValue',
        field: 'actualValue',
        width: 120,
        sortable: true,
        type: 'rightAligned',
        cellRenderer: (p: ICellRendererParams<Mismatch>) => {
          const d = p.data;
          if (!d) return '';
          const diff = d.expectedValue !== d.actualValue;
          const cls = diff
            ? 'font-mono text-xs bg-[color-mix(in_srgb,var(--status-error)_12%,transparent)]'
            : 'font-mono text-xs';
          return `<span class="${cls}">${escapeHtml(d.actualValue)}</span>`;
        },
      },
      {
        headerName: tr.instant('mismatches.grid.delta'),
        headerTooltip: tr.instant('mismatches.grid.delta'),
        colId: 'deltaPct',
        field: 'deltaPct',
        width: 70,
        sortable: true,
        cellClass: 'font-mono text-xs',
        type: 'rightAligned',
        valueFormatter: (p: ValueFormatterParams<Mismatch>) => formatDelta(p.value),
      },
      {
        headerName: tr.instant('mismatches.grid.detected'),
        headerTooltip: tr.instant('mismatches.grid.detected'),
        colId: 'detectedAt',
        field: 'detectedAt',
        width: 120,
        sortable: true,
        cellRenderer: (p: ICellRendererParams<Mismatch>) => {
          const v = p.data?.detectedAt;
          if (!v) return '\u2014';
          const abs = new Date(v).toLocaleString('ru-RU');
          const rel = formatDistanceToNow(new Date(v), { locale: ru, addSuffix: true });
          return `<span title="${escapeHtml(abs)}">${escapeHtml(rel)}</span>`;
        },
      },
      {
        headerName: tr.instant('mismatches.grid.status'),
        headerTooltip: tr.instant('mismatches.grid.status'),
        colId: 'status',
        field: 'status',
        width: 130,
        sortable: true,
        cellRenderer: (p: ICellRendererParams<Mismatch>) => {
          const st = p.data?.status;
          if (!st) return '';
          const color = STATUS_BADGE[st] ?? 'neutral';
          const cssVar = `var(--status-${color})`;
          const label = tr.instant(`mismatches.status.${st}`);
          return renderBadge(escapeHtml(label), cssVar);
        },
      },
      {
        headerName: tr.instant('mismatches.grid.resolution'),
        colId: 'resolution',
        field: 'resolution',
        width: 140,
        sortable: true,
        valueFormatter: (p: ValueFormatterParams<Mismatch>) => {
          const r = p.value as string | null;
          if (!r) return '\u2014';
          return tr.instant(`mismatches.resolution.${r}`);
        },
      },
      {
        headerName: tr.instant('mismatches.grid.connection'),
        colId: 'connectionName',
        field: 'connectionName',
        tooltipField: 'connectionName',
        width: 160,
        sortable: false,
        hide: true,
      },
      {
        headerName: tr.instant('mismatches.grid.severity'),
        headerTooltip: tr.instant('mismatches.grid.severity'),
        colId: 'severity',
        field: 'severity',
        width: 90,
        sortable: true,
        hide: true,
        cellRenderer: (p: ICellRendererParams<Mismatch>) => {
          const sev = p.data?.severity;
          if (!sev) return '';
          const color = sev === 'CRITICAL' ? 'var(--status-error)' : 'var(--status-warning)';
          return `<span style="color:${color}">\u26A0</span>`;
        },
      },
    ];
  }

  onGridReady(e: GridReadyEvent<Mismatch>): void {
    this.gridApi = e.api;
  }

  onConnectionChange(value: number | null): void {
    this.connectionId.set(value);
    this.applyFilters();
  }

  onSearchInput(value: string): void {
    this.searchSubject.next(value);
  }

  private navigateToMismatchDetail(row: Mismatch): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { selected: row.mismatchId },
      queryParamsHandling: 'merge',
    });
  }

  onCellContextMenu(e: CellContextMenuEvent<Mismatch>): void {
    const row = e.data;
    const evt = e.event as MouseEvent;
    if (!row || !evt) return;
    evt.preventDefault();
    this.contextMenu.set({ x: evt.clientX, y: evt.clientY, row });
  }

  onSelectionChanged(e: SelectionChangedEvent<Mismatch>): void {
    this.selectedRows.set(e.api.getSelectedRows());
  }

  onSortChanged(e: SortChangedEvent<Mismatch>): void {
    const sorted = e.api.getColumnState()?.filter((c) => c.sort != null);
    if (!sorted?.length) return;
    const c = sorted[0];
    const colId = c.colId ?? 'detectedAt';
    const fieldMap: Record<string, string> = {
      offerName: 'offerName',
      type: 'type',
      expectedValue: 'expectedValue',
      actualValue: 'actualValue',
      deltaPct: 'deltaPct',
      detectedAt: 'detectedAt',
      status: 'status',
      severity: 'severity',
      resolution: 'resolution',
    };
    const apiField = fieldMap[colId] ?? 'detectedAt';
    const dir = c.sort === 'asc' ? 'asc' : 'desc';
    this.sortParam.set(`${apiField},${dir}`);
    this.currentPage.set(0);
  }

  onChartClick(params: Record<string, unknown>): void {
    const seriesType = params['seriesType'] as string;
    if (seriesType === 'pie') {
      const name = params['name'] as string;
      const typeMap: Record<string, MismatchType> = {};
      for (const t of this.typeOptions) {
        typeMap[this.translate.instant('mismatches.type.' + t)] = t;
      }
      const matched = typeMap[name];
      if (matched) {
        this.filterTypes.set([matched]);
        this.applyFilters();
      }
    }
  }

  exportCsv(): void {
    const ws = this.ws.currentWorkspaceId();
    if (!ws) return;
    lastValueFrom(this.api.exportCsv(ws, this.mismatchFilter()))
      .then(blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'mismatches.csv';
        a.click();
        URL.revokeObjectURL(url);
      })
      .catch(() => this.toast.error(this.translate.instant('mismatches.export.error')));
  }

  toggleColumn(colId: string): void {
    if (!this.gridApi) return;
    const col = this.gridApi.getColumn(colId);
    if (!col) return;
    const visible = col.isVisible();
    this.gridApi.setColumnsVisible([colId], !visible);
  }

  isColumnVisible(colId: string): boolean {
    if (!this.gridApi) return true;
    const col = this.gridApi.getColumn(colId);
    return col ? col.isVisible() : true;
  }

  closeDetail(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { selected: null },
      queryParamsHandling: 'merge',
    });
  }

  applyFilters(): void {
    this.currentPage.set(0);
    this.persistFilters();
    this.invalidateList();
  }

  resetFilters(): void {
    this.connectionId.set(null);
    this.filterTypes.set([]);
    this.filterStatuses.set(['ACTIVE']);
    this.filterSeverities.set([]);
    this.periodFrom.set('');
    this.periodTo.set('');
    this.searchQuery.set('');
    this.currentPage.set(0);
    this.persistFilters();
    this.invalidateList();
  }

  toggleType(t: MismatchType): void {
    const current = this.filterTypes();
    this.filterTypes.set(
      current.includes(t) ? current.filter((x) => x !== t) : [...current, t],
    );
    this.applyFilters();
  }

  toggleStatus(s: MismatchStatus): void {
    const current = this.filterStatuses();
    const next = current.includes(s)
      ? current.filter((x) => x !== s)
      : [...current, s];
    this.filterStatuses.set(next.length ? next : ['ACTIVE']);
    this.applyFilters();
  }

  toggleSeverity(sev: MismatchSeverity): void {
    const current = this.filterSeverities();
    this.filterSeverities.set(
      current.includes(sev) ? current.filter((x) => x !== sev) : [...current, sev],
    );
    this.applyFilters();
  }

  onPageChange(event: { page: number; pageSize: number }): void {
    this.currentPage.set(event.page);
    this.pageSize.set(event.pageSize);
  }

  bulkAcknowledge(): void {
    const ids = this.selectedRows().map((r) => r.mismatchId);
    if (ids.length === 0) return;
    this.bulkAckMutation.mutate(ids);
  }

  openBulkIgnoreModal(): void {
    this.bulkIgnoreReason.set('');
    this.showBulkIgnoreModal.set(true);
  }

  confirmBulkIgnore(): void {
    const ids = this.selectedRows().map((r) => r.mismatchId);
    const reason = this.bulkIgnoreReason().trim();
    if (ids.length === 0 || !reason) return;
    this.bulkIgnoreMutation.mutate({ ids, reason });
  }

  cancelBulkIgnore(): void {
    this.showBulkIgnoreModal.set(false);
    this.bulkIgnoreReason.set('');
  }

  clearBulkSelection(): void {
    this.clearGridSelection();
  }

  ngOnInit(): void {
    this.restoreFilters();
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(e: KeyboardEvent): void {
    if (e.key === 'Escape') {
      if (this.selectedMismatchId()) {
        this.closeDetail();
        e.preventDefault();
      } else if (this.contextMenu()) {
        this.contextMenu.set(null);
        e.preventDefault();
      }
    }
    if (e.ctrlKey && e.key === 'f') {
      e.preventDefault();
      this.searchInputRef?.nativeElement.focus();
    }
    if (e.ctrlKey && e.shiftKey && e.key === 'E') {
      e.preventDefault();
      this.exportCsv();
    }
  }

  @HostListener('document:click')
  onDocClick(): void {
    this.contextMenu.set(null);
    this.showColumnsPanel.set(false);
  }

  onContextMenu(event: MouseEvent, row: Mismatch): void {
    event.preventDefault();
    this.contextMenu.set({ x: event.clientX, y: event.clientY, row });
  }

  contextOpenDetail(): void {
    const ctx = this.contextMenu();
    if (!ctx) return;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { selected: ctx.row.mismatchId },
      queryParamsHandling: 'merge',
    });
    this.contextMenu.set(null);
  }

  contextGoToProduct(): void {
    const ctx = this.contextMenu();
    if (!ctx) return;
    void this.router.navigate(
      ['/workspace', this.ws.currentWorkspaceId(), 'grid'],
      { queryParams: { offerId: ctx.row.offerId } },
    );
    this.contextMenu.set(null);
  }

  contextCopySku(): void {
    const ctx = this.contextMenu();
    if (!ctx) return;
    navigator.clipboard.writeText(ctx.row.skuCode)
      .then(() => this.toast.info(this.translate.instant('mismatches.context.sku_copied')))
      .catch(() => this.toast.error(this.translate.instant('mismatches.context.copy_failed')));
    this.contextMenu.set(null);
  }

  contextIgnore(): void {
    const ctx = this.contextMenu();
    if (!ctx) return;
    this.contextMenu.set(null);
    this.selectedRows.set([ctx.row]);
    this.openBulkIgnoreModal();
  }

  private applyPulseAnimation(evt: MismatchWsEvent): void {
    if (!this.gridApi) return;
    const rowNode = this.gridApi.getRowNode(String(evt.mismatchId));
    if (!rowNode) return;
    const el = document.querySelector<HTMLElement>(
      `[row-id="${rowNode.id}"]`,
    );
    if (!el) return;
    const cssClass = evt.eventType === 'MISMATCH_DETECTED'
      ? 'dp-pulse-new'
      : evt.eventType === 'MISMATCH_RESOLVED'
        ? 'dp-pulse-resolved'
        : 'dp-pulse-acknowledged';
    el.classList.add(cssClass);
    setTimeout(() => el.classList.remove(cssClass), 2000);
  }

  private showThrottledCriticalToast(evt: MismatchWsEvent): void {
    const now = Date.now();
    this.wsToastTimestamps = this.wsToastTimestamps.filter(
      t => now - t < this.WS_TOAST_WINDOW_MS,
    );
    if (this.wsToastTimestamps.length >= this.WS_TOAST_LIMIT) return;
    this.wsToastTimestamps.push(now);
    const delta = evt.deltaPct != null ? `${evt.deltaPct > 0 ? '+' : ''}${evt.deltaPct.toFixed(1)}%` : '';
    this.toast.error(
      this.translate.instant('mismatches.ws.critical_detected', {
        name: evt.offerName,
        type: this.translate.instant('mismatches.type.' + evt.type),
        delta,
      }),
    );
  }

  private static readonly PAGE_KEY = 'mismatches:dashboard';

  private persistFilters(): void {
    this.viewState.save(MismatchDashboardPageComponent.PAGE_KEY, {
      filters: {
        connectionId: this.connectionId(),
        types: this.filterTypes(),
        statuses: this.filterStatuses(),
        severities: this.filterSeverities(),
        from: this.periodFrom(),
        to: this.periodTo(),
      },
    });
  }

  private restoreFilters(): void {
    const persisted = this.viewState.restoreFilters(MismatchDashboardPageComponent.PAGE_KEY);
    if (!persisted) return;
    if (persisted['connectionId'] != null) this.connectionId.set(persisted['connectionId']);
    if (persisted['types']?.length) this.filterTypes.set(persisted['types']);
    if (persisted['statuses']?.length) this.filterStatuses.set(persisted['statuses']);
    if (persisted['severities']?.length) this.filterSeverities.set(persisted['severities']);
    if (persisted['from']) this.periodFrom.set(persisted['from']);
    if (persisted['to']) this.periodTo.set(persisted['to']);
  }

  private clearGridSelection(): void {
    this.gridApi?.deselectAll();
    this.selectedRows.set([]);
  }

  private invalidateList(): void {
    this.queryClient.invalidateQueries({ queryKey: ['mismatches'] });
    this.queryClient.invalidateQueries({ queryKey: ['mismatch-summary'] });
  }
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function formatDelta(v: unknown): string {
  if (v === null || v === undefined) return '\u2014';
  const n = Number(v);
  if (Number.isNaN(n)) return '\u2014';
  return `${n > 0 ? '+' : ''}${n.toFixed(1).replace('.', ',')}%`;
}
