import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AgGridAngular } from 'ag-grid-angular';
import {
  ColDef,
  GetRowIdParams,
  GridApi,
  GridReadyEvent,
  ICellRendererParams,
  RowClickedEvent,
  SelectionChangedEvent,
  SortChangedEvent,
  ValueFormatterParams,
} from 'ag-grid-community';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { map } from 'rxjs/operators';

import { MismatchApiService } from '@core/api/mismatch-api.service';
import { ConnectionApiService } from '@core/api/connection-api.service';
import {
  Mismatch,
  MismatchFilter,
  MismatchSeverity,
  MismatchStatus,
  MismatchType,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { MismatchDetailPanelComponent } from './mismatch-detail-panel.component';

const PAGE_SIZE = 25;

const STATUS_BADGE: Record<string, 'success' | 'error' | 'warning' | 'info' | 'neutral'> = {
  ACTIVE: 'warning',
  ACKNOWLEDGED: 'info',
  RESOLVED: 'success',
  AUTO_RESOLVED: 'success',
  IGNORED: 'neutral',
};

@Component({
  selector: 'dp-mismatch-dashboard-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    AgGridAngular,
    KpiCardComponent,
    EmptyStateComponent,
    MismatchDetailPanelComponent,
  ],
  templateUrl: './mismatch-dashboard-page.component.html',
})
export class MismatchDashboardPageComponent {
  private readonly api = inject(MismatchApiService);
  private readonly connectionApi = inject(ConnectionApiService);
  private readonly ws = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);

  private gridApi: GridApi<Mismatch> | null = null;

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
  readonly searchQuery = signal('');

  readonly currentPage = signal(0);
  readonly sortParam = signal('detectedAt,desc');

  readonly selectedRows = signal<Mismatch[]>([]);

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
      this.sortParam(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.api.list(
          this.ws.currentWorkspaceId()!,
          this.mismatchFilter(),
          this.currentPage(),
          PAGE_SIZE,
          this.sortParam(),
        ),
      ),
    enabled: !!this.ws.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.listQuery.data()?.content ?? []);
  readonly totalPages = computed(() => this.listQuery.data()?.totalPages ?? 0);
  readonly totalElements = computed(() => this.listQuery.data()?.totalElements ?? 0);

  readonly avgHoursDisplay = computed(() => {
    const v = this.summaryQuery.data()?.avgHoursUnresolved;
    if (v == null) return null;
    return v.toFixed(1).replace('.', ',');
  });

  readonly columnDefs!: ColDef<Mismatch>[];
  readonly defaultColDef: ColDef<Mismatch> = {
    resizable: true,
    suppressHeaderMenuButton: true,
  };

  readonly getRowId = (params: GetRowIdParams<Mismatch>) =>
    String(params.data?.mismatchId ?? '');

  readonly bulkAckMutation = injectMutation(() => ({
    mutationFn: async (ids: number[]) => {
      const ws = this.ws.currentWorkspaceId()!;
      for (const id of ids) {
        await lastValueFrom(this.api.acknowledge(ws, id));
      }
    },
    onSuccess: () => {
      this.toast.success(this.translate.instant('mismatches.toast.bulk_ack'));
      this.clearGridSelection();
      this.invalidateList();
    },
    onError: () => this.toast.error(this.translate.instant('mismatches.toast.error')),
  }));

  constructor() {
    const tr = this.translate;
    this.columnDefs = [
      {
        colId: 'selection',
        headerCheckboxSelection: true,
        checkboxSelection: true,
        width: 44,
        pinned: 'left',
        sortable: false,
        suppressMovable: true,
      },
      {
        headerName: tr.instant('mismatches.grid.offer'),
        colId: 'offerName',
        field: 'offerName',
        minWidth: 220,
        pinned: 'left',
        sortable: true,
        cellRenderer: (p: ICellRendererParams<Mismatch>) => {
          const d = p.data;
          if (!d) return '';
          return `<div class="leading-tight py-1"><div class="font-medium">${escapeHtml(d.offerName)}</div><div class="text-[11px] text-[var(--text-secondary)]">${escapeHtml(d.skuCode)}</div></div>`;
        },
      },
      {
        headerName: 'MP',
        colId: 'marketplaceType',
        field: 'marketplaceType',
        width: 72,
        sortable: true,
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
        colId: 'type',
        field: 'type',
        width: 100,
        sortable: true,
        cellRenderer: (p: ICellRendererParams<Mismatch>) => {
          const v = p.data?.type;
          if (!v) return '';
          return `<span class="rounded-full bg-[color-mix(in_srgb,var(--accent-primary)_12%,transparent)] px-2 py-0.5 text-[11px] font-medium text-[var(--accent-primary)]">${escapeHtml(v)}</span>`;
        },
      },
      {
        headerName: tr.instant('mismatches.grid.expected'),
        field: 'expectedValue',
        width: 110,
        sortable: true,
        cellClass: 'font-mono text-xs',
      },
      {
        headerName: tr.instant('mismatches.grid.actual'),
        field: 'actualValue',
        width: 110,
        sortable: true,
        cellClass: 'font-mono text-xs',
      },
      {
        headerName: tr.instant('mismatches.grid.delta'),
        colId: 'deltaPct',
        field: 'deltaPct',
        width: 88,
        sortable: true,
        cellClass: 'font-mono text-xs',
        valueFormatter: (p: ValueFormatterParams<Mismatch>) => formatDelta(p.value),
      },
      {
        headerName: tr.instant('mismatches.grid.detected'),
        colId: 'detectedAt',
        field: 'detectedAt',
        width: 140,
        sortable: true,
        valueFormatter: (p: ValueFormatterParams<Mismatch>) =>
          p.value ? new Date(p.value as string).toLocaleString('ru-RU') : '—',
      },
      {
        headerName: tr.instant('mismatches.grid.status'),
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
          return `<span class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium" style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}"><span class="inline-block h-1.5 w-1.5 rounded-full" style="background:${cssVar}"></span>${escapeHtml(label)}</span>`;
        },
      },
      {
        headerName: tr.instant('mismatches.grid.resolution'),
        colId: 'resolution',
        field: 'resolution',
        width: 120,
        sortable: true,
        valueFormatter: (p: ValueFormatterParams<Mismatch>) => {
          const r = p.value as string | null;
          if (!r) return '—';
          return tr.instant(`mismatches.resolution.${r}`);
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

  onRowClicked(e: RowClickedEvent<Mismatch>): void {
    const target = e.event?.target as HTMLElement | undefined;
    if (target?.closest('.ag-checkbox-input,.ag-selection-checkbox')) {
      return;
    }
    const row = e.data;
    if (!row) return;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { selected: row.mismatchId },
      queryParamsHandling: 'merge',
    });
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
      selection: 'mismatchId',
      offerName: 'offerName',
      marketplaceType: 'marketplaceType',
      type: 'type',
      expectedValue: 'expectedValue',
      actualValue: 'actualValue',
      deltaPct: 'deltaPct',
      detectedAt: 'detectedAt',
      status: 'status',
      resolution: 'resolution',
    };
    const apiField = fieldMap[colId] ?? 'detectedAt';
    const dir = c.sort === 'asc' ? 'asc' : 'desc';
    this.sortParam.set(`${apiField},${dir}`);
    this.currentPage.set(0);
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
    this.invalidateList();
  }

  onTypesChange(e: Event): void {
    const el = e.target as HTMLSelectElement;
    const v = Array.from(el.selectedOptions, (o) => o.value as MismatchType);
    this.filterTypes.set(v);
  }

  onStatusesChange(e: Event): void {
    const el = e.target as HTMLSelectElement;
    const v = Array.from(el.selectedOptions, (o) => o.value as MismatchStatus);
    this.filterStatuses.set(v.length ? v : ['ACTIVE']);
  }

  onSeveritiesChange(e: Event): void {
    const el = e.target as HTMLSelectElement;
    const v = Array.from(el.selectedOptions, (o) => o.value as MismatchSeverity);
    this.filterSeverities.set(v);
  }

  prevPage(): void {
    if (this.currentPage() > 0) {
      this.currentPage.update((p) => p - 1);
    }
  }

  nextPage(): void {
    if (this.currentPage() < this.totalPages() - 1) {
      this.currentPage.update((p) => p + 1);
    }
  }

  bulkAcknowledge(): void {
    const ids = this.selectedRows().map((r) => r.mismatchId);
    if (ids.length === 0) return;
    this.bulkAckMutation.mutate(ids);
  }

  clearBulkSelection(): void {
    this.clearGridSelection();
  }

  hasActiveFilters(): boolean {
    return (
      this.connectionId() != null ||
      this.filterTypes().length > 0 ||
      JSON.stringify(this.filterStatuses()) !== JSON.stringify(['ACTIVE']) ||
      this.filterSeverities().length > 0 ||
      this.periodFrom().trim() !== '' ||
      this.periodTo().trim() !== '' ||
      this.searchQuery().trim() !== ''
    );
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
  if (v === null || v === undefined) return '—';
  const n = Number(v);
  if (Number.isNaN(n)) return '—';
  return `${n > 0 ? '+' : ''}${n.toFixed(1).replace('.', ',')}%`;
}
