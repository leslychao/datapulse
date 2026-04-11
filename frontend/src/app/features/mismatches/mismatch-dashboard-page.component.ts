import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  HostListener,
  inject,
  signal,
  ViewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectMutation,
  injectQuery,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { map } from 'rxjs/operators';
import type { EChartsOption } from 'echarts';

import { MismatchApiService } from '@core/api/mismatch-api.service';
import { ConnectionApiService } from '@core/api/connection-api.service';
import {
  Mismatch,
  MismatchFilter,
  MismatchType,
  MismatchWsEvent,
} from '@core/models';
import { WebSocketService } from '@core/websocket/websocket.service';
import { RbacService } from '@core/auth/rbac.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ViewStateService } from '@shared/services/view-state.service';
import { ToastService } from '@shared/shell/toast/toast.service';
import {
  FilterBarComponent,
  FilterConfig,
} from '@shared/components/filter-bar/filter-bar.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { PaginationBarComponent } from '@shared/components/pagination-bar/pagination-bar.component';
import {
  FilterBarUrlDef,
  hasUrlState,
  readFilterBarFromUrl,
  SortUrlState,
  syncFilterBarToUrl,
  readSortFromUrl,
  syncSortToUrl,
} from '@shared/utils/url-filters';

import { MismatchKpiStripComponent } from './mismatch-kpi-strip.component';
import { MismatchChartsSectionComponent } from './mismatch-charts-section.component';
import { MismatchToolbarComponent, ToggleableColumn } from './mismatch-toolbar.component';
import { MismatchGridComponent } from './mismatch-grid.component';
import { MismatchDetailPanelComponent } from './mismatch-detail-panel.component';

const DEFAULT_PAGE_SIZE = 50;
const VS_KEY = 'mismatches:dashboard';
const VS_CHARTS_KEY = 'mismatches:charts';

const FILTER_URL_DEFS: FilterBarUrlDef[] = [
  { key: 'type', type: 'csv' },
  { key: 'status', type: 'csv' },
  { key: 'severity', type: 'csv' },
  { key: 'connectionId', type: 'string' },
  { key: 'period', type: 'date-range' },
  { key: 'query', type: 'string' },
];

const DEFAULT_SORT: SortUrlState = { column: 'detectedAt', direction: 'desc' };

@Component({
  selector: 'dp-mismatch-dashboard-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    FilterBarComponent,
    EmptyStateComponent,
    PaginationBarComponent,
    MismatchKpiStripComponent,
    MismatchChartsSectionComponent,
    MismatchToolbarComponent,
    MismatchGridComponent,
    MismatchDetailPanelComponent,
  ],
  templateUrl: './mismatch-dashboard-page.component.html',
})
export class MismatchDashboardPageComponent {
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

  @ViewChild('mismatchGrid') private gridRef?: MismatchGridComponent;
  private readonly gridApiSignal = signal<import('ag-grid-community').GridApi | null>(null);

  readonly filterBarValues = signal<Record<string, any>>({ status: ['ACTIVE'] });
  readonly currentPage = signal(0);
  readonly pageSize = signal<number>(DEFAULT_PAGE_SIZE);
  readonly sortState = signal<SortUrlState>(DEFAULT_SORT);
  readonly selectedRows = signal<Mismatch[]>([]);
  readonly chartsCollapsed = signal(true);
  readonly showBulkIgnoreModal = signal(false);
  readonly bulkIgnoreReason = signal('');
  readonly contextMenu = signal<{ x: number; y: number; row: Mismatch } | null>(null);

  private readonly selectedFromQuery = toSignal(
    this.route.queryParamMap.pipe(map((m) => m.get('selected'))),
    { initialValue: null },
  );

  readonly selectedMismatchId = computed(() => {
    const s = this.selectedFromQuery();
    if (!s) return null;
    const n = Number(s);
    return Number.isFinite(n) ? n : null;
  });

  readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections'],
    queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
  }));

  readonly filterConfigs = computed((): FilterConfig[] => {
    const conns = this.connectionsQuery.data() ?? [];
    return [
      { key: 'type', label: 'mismatches.filter.type', type: 'multi-select',
        options: (['PRICE', 'STOCK', 'PROMO', 'FINANCE'] as const).map(t => ({
          value: t, label: 'mismatches.type.' + t,
        })) },
      { key: 'status', label: 'mismatches.filter.status', type: 'multi-select',
        options: (['ACTIVE', 'ACKNOWLEDGED', 'RESOLVED', 'AUTO_RESOLVED', 'IGNORED'] as const).map(s => ({
          value: s, label: 'mismatches.status.' + s,
        })) },
      { key: 'severity', label: 'mismatches.filter.severity', type: 'multi-select',
        options: (['WARNING', 'CRITICAL'] as const).map(s => ({
          value: s, label: 'mismatches.severity.' + s,
        })) },
      { key: 'connectionId', label: 'mismatches.filter.connection', type: 'select',
        options: conns.map(c => ({ value: String(c.id), label: c.name })) },
      { key: 'period', label: 'mismatches.filter.period', type: 'date-range' },
      { key: 'query', label: 'mismatches.filter.search', type: 'text' },
    ];
  });

  readonly mismatchFilter = computed((): MismatchFilter => {
    const vals = this.filterBarValues();
    const f: MismatchFilter = {};
    const types = vals['type'];
    if (Array.isArray(types) && types.length) f.type = types;
    const st = vals['status'];
    if (Array.isArray(st) && st.length) f.status = st; else f.status = ['ACTIVE'];
    const sev = vals['severity'];
    if (Array.isArray(sev) && sev.length) f.severity = sev;
    const cid = vals['connectionId'];
    if (cid) f.connectionId = [Number(cid)];
    const period = vals['period'];
    if (period?.from) f.from = period.from;
    if (period?.to) f.to = period.to;
    const q = vals['query'];
    if (q) f.query = q;
    return f;
  });

  readonly sortParam = computed(() =>
    `${this.sortState().column},${this.sortState().direction}`,
  );

  readonly summaryQuery = injectQuery(() => ({
    queryKey: ['mismatch-summary', this.ws.currentWorkspaceId()],
    queryFn: () => lastValueFrom(this.api.getSummary(this.ws.currentWorkspaceId()!)),
    enabled: !!this.ws.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly listQuery = injectQuery(() => ({
    queryKey: [
      'mismatches', this.ws.currentWorkspaceId(), this.mismatchFilter(),
      this.currentPage(), this.pageSize(), this.sortParam(),
    ],
    queryFn: () => lastValueFrom(
      this.api.list(
        this.ws.currentWorkspaceId()!, this.mismatchFilter(),
        this.currentPage(), this.pageSize(), this.sortParam(),
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

  readonly hasActiveFilters = computed(() => {
    const vals = this.filterBarValues();
    for (const [key, v] of Object.entries(vals)) {
      if (key === 'status') {
        if (Array.isArray(v) && v.length === 1 && v[0] === 'ACTIVE') continue;
        if (Array.isArray(v) && v.length > 0) return true;
        continue;
      }
      if (v !== '' && v !== null && v !== undefined) {
        if (Array.isArray(v) && v.length === 0) continue;
        if (typeof v === 'object' && !Array.isArray(v) && !v?.from && !v?.to) continue;
        return true;
      }
    }
    return false;
  });

  readonly toggleableColumns = computed((): ToggleableColumn[] => {
    const api = this.gridApiSignal();
    if (!api || !this.gridRef) return [];
    return this.gridRef.columnDefs
      .filter(c => c.colId && c.colId !== 'quickAction')
      .map(c => ({
        colId: c.colId!,
        headerName: c.headerName ?? c.colId ?? '',
        visible: this.gridRef!.isColumnVisible(c.colId!),
      }));
  });

  readonly prevMismatchId = computed(() => {
    const id = this.selectedMismatchId();
    if (!id) return null;
    const r = this.rows();
    const idx = r.findIndex(m => m.mismatchId === id);
    return idx > 0 ? r[idx - 1].mismatchId : null;
  });

  readonly nextMismatchId = computed(() => {
    const id = this.selectedMismatchId();
    if (!id) return null;
    const r = this.rows();
    const idx = r.findIndex(m => m.mismatchId === id);
    return idx >= 0 && idx < r.length - 1 ? r[idx + 1].mismatchId : null;
  });

  readonly donutOptions = computed((): EChartsOption => {
    const dist = this.summaryQuery.data()?.distributionByType ?? [];
    const colorMap: Record<string, string> = {
      PRICE: '#2563EB', STOCK: '#D97706', PROMO: '#7C3AED', FINANCE: '#4338CA',
    };
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', right: 10, top: 'center', textStyle: { fontSize: 12 } },
      series: [{
        type: 'pie', radius: ['45%', '75%'], avoidLabelOverlap: false,
        label: { show: false }, emphasis: { label: { show: false } },
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
    const tr = this.translate;
    return {
      tooltip: { trigger: 'axis' },
      legend: {
        data: [tr.instant('mismatches.charts.new'), tr.instant('mismatches.charts.resolved')],
        bottom: 0, textStyle: { fontSize: 11 },
      },
      grid: { left: 40, right: 16, top: 8, bottom: 32 },
      xAxis: { type: 'category', data: tl.map(d => d.date.slice(5).replace('-', '.')), axisLabel: { fontSize: 10 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: tr.instant('mismatches.charts.new'), type: 'bar', stack: 'a',
          data: tl.map(d => d.newCount), itemStyle: { color: 'rgba(220,38,38,0.5)' } },
        { name: tr.instant('mismatches.charts.resolved'), type: 'bar', stack: 'a',
          data: tl.map(d => d.resolvedCount), itemStyle: { color: 'rgba(5,150,105,0.5)' } },
      ],
    };
  });

  private wsToastTimestamps: number[] = [];

  readonly bulkAckMutation = injectMutation(() => ({
    mutationFn: (ids: number[]) =>
      lastValueFrom(this.api.bulkAcknowledge(this.ws.currentWorkspaceId()!, ids)),
    onSuccess: () => {
      this.toast.success(this.translate.instant('mismatches.toast.bulk_ack'));
      this.clearSelection();
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
      this.clearSelection();
      this.invalidateList();
    },
    onError: () => this.toast.error(this.translate.instant('mismatches.toast.error')),
  }));

  readonly quickAckMutation = injectMutation(() => ({
    mutationFn: (id: number) =>
      lastValueFrom(this.api.acknowledge(this.ws.currentWorkspaceId()!, id)),
    onSuccess: () => {
      this.toast.success(this.translate.instant('mismatches.toast.ack'));
      this.invalidateList();
    },
    onError: () => this.toast.error(this.translate.instant('mismatches.toast.error')),
  }));

  constructor() {
    this.initUrlSync();
    this.restoreChartsState();

    effect(() => {
      const evt = this.webSocket.lastMismatchEvent();
      if (!evt) return;
      this.gridRef?.applyPulseAnimation(evt);
      if (evt.eventType === 'MISMATCH_DETECTED' && evt.severity === 'CRITICAL') {
        this.showThrottledCriticalToast(evt);
      }
    });

    effect(() => {
      this.viewState.save(VS_CHARTS_KEY, {
        filters: { collapsed: this.chartsCollapsed() },
      });
    });
  }

  onFiltersChanged(values: Record<string, any>): void {
    const patched = { ...values };
    if (!patched['status'] || (Array.isArray(patched['status']) && patched['status'].length === 0)) {
      patched['status'] = ['ACTIVE'];
    }
    this.filterBarValues.set(patched);
    this.currentPage.set(0);
  }

  resetFilters(): void {
    this.filterBarValues.set({ status: ['ACTIVE'] });
    this.currentPage.set(0);
  }

  onSortChanged(event: { column: string; direction: string }): void {
    const fieldMap: Record<string, string> = { age: 'detectedAt' };
    const col = fieldMap[event.column] ?? event.column;
    if (col) {
      this.sortState.set({ column: col, direction: event.direction as 'asc' | 'desc' });
    }
    this.currentPage.set(0);
  }

  onPageChange(event: { page: number; pageSize: number }): void {
    this.currentPage.set(event.page);
    this.pageSize.set(event.pageSize);
  }

  onChartClick(params: Record<string, unknown>): void {
    if (params['seriesType'] !== 'pie') return;
    const name = params['name'] as string;
    const typeMap: Record<string, MismatchType> = {};
    for (const t of ['PRICE', 'STOCK', 'PROMO', 'FINANCE'] as MismatchType[]) {
      typeMap[this.translate.instant('mismatches.type.' + t)] = t;
    }
    const matched = typeMap[name];
    if (matched) {
      this.filterBarValues.update(v => ({ ...v, type: [matched] }));
      this.currentPage.set(0);
    }
  }

  openDetailPage(id: number): void {
    void this.router.navigate([id], { relativeTo: this.route });
  }

  navigateToMismatch(id: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { selected: id },
      queryParamsHandling: 'merge',
    });
  }

  closeDetail(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { selected: null },
      queryParamsHandling: 'merge',
    });
  }

  onGridApiReady(api: import('ag-grid-community').GridApi): void {
    this.gridApiSignal.set(api);
  }

  toggleColumn(colId: string): void {
    this.gridRef?.toggleColumn(colId);
    this.gridApiSignal.update(v => v);
  }

  toggleCharts(): void {
    this.chartsCollapsed.update(v => !v);
  }

  exportCsv(): void {
    const wsId = this.ws.currentWorkspaceId();
    if (!wsId) return;
    lastValueFrom(this.api.exportCsv(wsId, this.mismatchFilter()))
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

  bulkAcknowledge(): void {
    const ids = this.selectedRows().map(r => r.mismatchId);
    if (!ids.length) return;
    this.bulkAckMutation.mutate(ids);
  }

  openBulkIgnoreModal(): void {
    this.bulkIgnoreReason.set('');
    this.showBulkIgnoreModal.set(true);
  }

  confirmBulkIgnore(): void {
    const ids = this.selectedRows().map(r => r.mismatchId);
    const reason = this.bulkIgnoreReason().trim();
    if (!ids.length || !reason) return;
    this.bulkIgnoreMutation.mutate({ ids, reason });
  }

  cancelBulkIgnore(): void {
    this.showBulkIgnoreModal.set(false);
    this.bulkIgnoreReason.set('');
  }

  onContextMenuAction(type: string): void {
    const ctx = this.contextMenu();
    if (!ctx) return;
    this.contextMenu.set(null);
    switch (type) {
      case 'open':
        this.openDetailPage(ctx.row.mismatchId);
        break;
      case 'goProduct':
        void this.router.navigate(
          ['/workspace', this.ws.currentWorkspaceId(), 'grid'],
          { queryParams: { offerId: ctx.row.offerId } },
        );
        break;
      case 'copySku':
        navigator.clipboard.writeText(ctx.row.skuCode)
          .then(() => this.toast.info(this.translate.instant('mismatches.context.sku_copied')))
          .catch(() => this.toast.error(this.translate.instant('mismatches.context.copy_failed')));
        break;
      case 'ignore':
        this.selectedRows.set([ctx.row]);
        this.openBulkIgnoreModal();
        break;
    }
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
    if (e.ctrlKey && e.shiftKey && e.key === 'E') {
      e.preventDefault();
      this.exportCsv();
    }
  }

  @HostListener('document:click')
  onDocClick(): void {
    this.contextMenu.set(null);
  }

  private initUrlSync(): void {
    const filterKeys = FILTER_URL_DEFS.map(d =>
      d.type === 'date-range' ? [d.key + '_from', d.key + '_to'] : [d.key],
    ).flat();
    const urlPresent = hasUrlState(this.route, [...filterKeys, 'sortBy', 'sortDir']);

    if (!urlPresent) {
      const persisted = this.viewState.restore(VS_KEY);
      if (persisted?.filters) {
        const restored = { ...persisted.filters };
        if (!restored['status']?.length) restored['status'] = ['ACTIVE'];
        this.filterBarValues.set(restored);
      }
      if (persisted?.sort) {
        this.sortState.set(persisted.sort);
      }
    } else {
      readFilterBarFromUrl(this.route, this.filterBarValues, FILTER_URL_DEFS);
      if (!this.filterBarValues()['status']?.length) {
        this.filterBarValues.update(v => ({ ...v, status: ['ACTIVE'] }));
      }
      readSortFromUrl(this.route, this.sortState);
    }

    syncFilterBarToUrl(this.router, this.route, this.filterBarValues, FILTER_URL_DEFS);
    syncSortToUrl(this.router, this.route, this.sortState, DEFAULT_SORT);

    effect(() => {
      const filters = this.filterBarValues();
      const sort = this.sortState();
      this.viewState.save(VS_KEY, { filters, sort });
    });
  }

  private restoreChartsState(): void {
    const saved = this.viewState.restore(VS_CHARTS_KEY);
    if (saved?.filters?.['collapsed'] != null) {
      this.chartsCollapsed.set(saved.filters['collapsed']);
    }
  }

  private clearSelection(): void {
    this.gridRef?.deselectAll();
    this.selectedRows.set([]);
  }

  private invalidateList(): void {
    this.queryClient.invalidateQueries({ queryKey: ['mismatches'] });
    this.queryClient.invalidateQueries({ queryKey: ['mismatch-summary'] });
  }

  private showThrottledCriticalToast(evt: MismatchWsEvent): void {
    const now = Date.now();
    this.wsToastTimestamps = this.wsToastTimestamps.filter(t => now - t < 10_000);
    if (this.wsToastTimestamps.length >= 3) return;
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
}
