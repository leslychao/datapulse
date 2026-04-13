import {
  ChangeDetectionStrategy,
  Component,
  computed,
  HostListener,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom, startWith } from 'rxjs';
import { ColDef, GridApi } from 'ag-grid-community';
import type { EChartsOption } from 'echarts';
import { LucideAngularModule, Download, Package, AlertTriangle, AlertCircle, DollarSign } from 'lucide-angular';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import {
  AnalyticsFilter,
  InventoryByProduct,
  StockOutRisk,
  getMarketplaceShortLabel,
} from '@core/models';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { PaginationBarComponent } from '@shared/components/pagination-bar/pagination-bar.component';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { StockRiskBadgeComponent } from '@shared/components/stock-risk-badge.component';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { createListPageState } from '@shared/utils/list-page-state';
import { formatMoney } from '@shared/utils/format.utils';
import { platformColumn } from '@shared/utils/column-factories';

const STOCK_RISKS: StockOutRisk[] = ['CRITICAL', 'WARNING', 'NORMAL'];

function resolveCssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || name;
}

function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

@Component({
  selector: 'dp-inventory-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    LucideAngularModule,
    DataGridComponent,
    PaginationBarComponent,
    FilterBarComponent,
    EmptyStateComponent,
    StockRiskBadgeComponent,
    ChartComponent,
    KpiCardComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <!-- KPI cards -->
    <div class="flex flex-wrap gap-3 pb-3">
      <dp-kpi-card
        [label]="'analytics.inventory.kpi.total_skus' | translate"
        [value]="kpiTotalSkus()"
        [icon]="PackageIcon"
        accent="neutral"
        [loading]="overviewQuery.isPending()"
      />
      <dp-kpi-card
        [label]="'analytics.inventory.kpi.critical' | translate"
        [value]="kpiCritical()"
        [icon]="AlertTriangleIcon"
        accent="error"
        [loading]="overviewQuery.isPending()"
        [clickable]="true"
        [active]="kpiRiskFilter() === 'CRITICAL'"
        (clicked)="toggleRiskFilter('CRITICAL')"
      />
      <dp-kpi-card
        [label]="'analytics.inventory.kpi.warning' | translate"
        [value]="kpiWarning()"
        [icon]="AlertCircleIcon"
        accent="warning"
        [loading]="overviewQuery.isPending()"
        [clickable]="true"
        [active]="kpiRiskFilter() === 'WARNING'"
        (clicked)="toggleRiskFilter('WARNING')"
      />
      <dp-kpi-card
        [label]="'analytics.inventory.kpi.frozen_capital' | translate"
        [value]="kpiFrozenCapital()"
        [icon]="DollarSignIcon"
        accent="neutral"
        [loading]="overviewQuery.isPending()"
      />
    </div>

    <!-- Filter bar + export -->
    <div class="flex items-center gap-2">
      <div class="flex-1">
        <dp-filter-bar
          [filters]="filterConfigs"
          [values]="listState.filterValues()"
          (filtersChanged)="listState.onFiltersChanged($event)"
        />
      </div>
      <button
        (click)="exportCsv()"
        class="flex shrink-0 items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--border-default)]
               bg-[var(--bg-primary)] px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-secondary)]
               transition-colors hover:bg-[var(--bg-tertiary)]"
      >
        <lucide-icon [img]="downloadIcon" size="14" />
        <span>{{ 'common.export_csv' | translate }}</span>
      </button>
    </div>

    <!-- Main content area: grid + optional detail panel -->
    <div class="mt-2 flex flex-1 gap-2 min-h-0">
      <div class="flex flex-1 flex-col min-h-0">
        @if (productsQuery.isError()) {
          <dp-empty-state
            [message]="'analytics.inventory.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="productsQuery.refetch()"
          />
        } @else if (!productsQuery.isPending() && gridRows().length === 0) {
          <dp-empty-state
            [message]="listState.hasActiveFilters()
              ? ('analytics.inventory.empty_filtered' | translate)
              : ('analytics.inventory.empty' | translate)"
            [actionLabel]="listState.hasActiveFilters()
              ? ('filter_bar.reset_all' | translate)
              : ''"
            (action)="listState.resetFilters()"
          />
        } @else {
          <dp-data-grid
            viewStateKey="analytics:inventory:by-product"
            [columnDefs]="columnDefs()"
            [rowData]="gridRows()"
            [loading]="productsQuery.isPending()"
            [pagination]="false"
            [pageSize]="listState.pageSize()"
            [getRowId]="getRowId"
            height="calc(100vh - 330px)"
            [initialSortModel]="listState.initialSortModel()"
            (sortChanged)="listState.onSortChanged($event)"
            (rowClicked)="onRowClicked($event)"
            (gridReady)="onGridReady($event)"
            [clickableRows]="true"
          />
        }

        @if (gridRows().length > 0) {
          <dp-pagination-bar
            [totalItems]="totalElements()"
            [pageSize]="listState.pageSize()"
            [currentPage]="listState.currentPage()"
            (pageChange)="listState.onPageChanged($event)"
          />
        }
      </div>

      <!-- Detail panel -->
      @if (selectedProduct(); as product) {
        <div
          class="flex w-[380px] shrink-0 flex-col border-l border-[var(--border-default)] bg-[var(--bg-primary)]"
          style="height: calc(100vh - 330px)"
        >
          <div class="flex items-center justify-between border-b border-[var(--border-default)] px-4 py-3">
            <h3 class="text-sm font-semibold text-[var(--text-primary)]">
              {{ 'analytics.inventory.detail.title' | translate }}
            </h3>
            <button
              class="cursor-pointer rounded p-1 text-[var(--text-tertiary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
              aria-label="Close"
              (click)="selectedProduct.set(null)"
            >
              ✕
            </button>
          </div>

          <div class="flex-1 space-y-4 overflow-auto p-4">
            <div>
              <p class="text-xs text-[var(--text-secondary)]">SKU</p>
              <p class="mt-0.5 font-mono text-sm text-[var(--text-primary)]">{{ product.skuCode }}</p>
            </div>
            <div>
              <p class="text-xs text-[var(--text-secondary)]">
                {{ 'analytics.inventory.col.platform' | translate }}
              </p>
              <p class="mt-0.5 text-sm text-[var(--text-primary)]">{{ mpShortLabel(product.sourcePlatform) }}</p>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <p class="text-xs text-[var(--text-secondary)]">
                  {{ 'analytics.inventory.col.available' | translate }}
                </p>
                <p class="mt-0.5 font-mono text-lg font-semibold text-[var(--text-primary)]">
                  {{ product.available }}
                </p>
              </div>
              <div>
                <p class="text-xs text-[var(--text-secondary)]">
                  {{ 'analytics.inventory.detail.reserved' | translate }}
                </p>
                <p class="mt-0.5 font-mono text-lg font-semibold text-[var(--text-primary)]">
                  {{ product.reserved }}
                </p>
              </div>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <p class="text-xs text-[var(--text-secondary)]">
                  {{ 'analytics.inventory.col.days_of_cover' | translate }}
                </p>
                <p class="mt-0.5 font-mono text-lg font-semibold text-[var(--text-primary)]">
                  {{ product.daysOfCover ?? '—' }}
                </p>
              </div>
              <div>
                <p class="text-xs text-[var(--text-secondary)]">
                  {{ 'analytics.inventory.col.risk' | translate }}
                </p>
                <p class="mt-1">
                  <dp-stock-risk-badge [risk]="product.stockOutRisk" />
                </p>
              </div>
            </div>
            <div>
              <p class="text-xs text-[var(--text-secondary)]">
                {{ 'analytics.inventory.detail.avg_daily_sales' | translate }}
              </p>
              <p class="mt-0.5 font-mono text-sm text-[var(--text-primary)]">
                {{ product.avgDailySales14d ?? '—' }}
              </p>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <p class="text-xs text-[var(--text-secondary)]">
                  {{ 'analytics.inventory.detail.cost_price' | translate }}
                </p>
                <p class="mt-0.5 font-mono text-sm text-[var(--text-primary)]">
                  {{ fmtMoney(product.costPrice) }}
                </p>
              </div>
              <div>
                <p class="text-xs text-[var(--text-secondary)]">
                  {{ 'analytics.inventory.col.frozen_capital' | translate }}
                </p>
                <p class="mt-0.5 font-mono text-sm font-semibold text-[var(--text-primary)]">
                  {{ fmtMoney(product.frozenCapital) }}
                </p>
              </div>
            </div>
            <div>
              <p class="text-xs text-[var(--text-secondary)]">
                {{ 'analytics.inventory.col.replenishment' | translate }}
              </p>
              <p class="mt-0.5 font-mono text-sm font-semibold text-[var(--text-primary)]">
                {{ product.recommendedReplenishment ?? '—' }}
              </p>
            </div>

            <!-- Mini stock history chart -->
            <div class="border-t border-[var(--border-subtle)] pt-3">
              <p class="mb-2 text-xs font-medium text-[var(--text-secondary)]">
                {{ 'analytics.inventory.detail.dynamics_30d' | translate }}
              </p>
              <dp-chart
                [options]="detailChartOptions()"
                [loading]="detailHistoryQuery.isPending()"
                height="140px"
              />
            </div>
          </div>
        </div>
      }
    </div>
  `,
})
export class InventoryPageComponent {

  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly t = inject(TranslateService);

  protected readonly PackageIcon = Package;
  protected readonly AlertTriangleIcon = AlertTriangle;
  protected readonly AlertCircleIcon = AlertCircle;
  protected readonly DollarSignIcon = DollarSign;
  protected readonly downloadIcon = Download;
  protected readonly mpShortLabel = getMarketplaceShortLabel;

  private gridApi: GridApi | null = null;

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.selectedProduct.set(null);
  }

  readonly selectedProduct = signal<InventoryByProduct | null>(null);

  readonly kpiRiskFilter = signal<StockOutRisk | null>(null);

  readonly listState = createListPageState({
    pageKey: 'analytics:inventory:by-product',
    defaultSort: { column: 'stockOutRisk', direction: 'asc' },
    defaultPageSize: 25,
    filterBarDefs: [
      { key: 'stockOutRisk', type: 'csv' },
      { key: 'sourcePlatform', type: 'csv' },
      { key: 'search', type: 'string' },
    ],
  });

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'stockOutRisk',
      label: 'analytics.inventory.filter.risk',
      type: 'multi-select',
      options: STOCK_RISKS.map((value) => ({
        value,
        label: `analytics.inventory.risk.${value.toLowerCase()}`,
      })),
    },
    {
      key: 'sourcePlatform',
      label: 'analytics.inventory.filter.marketplace',
      type: 'multi-select',
      options: [
        { value: 'WB', label: 'onboarding.connection.wb' },
        { value: 'OZON', label: 'onboarding.connection.ozon' },
      ],
    },
    {
      key: 'search',
      label: 'analytics.inventory.filter.search_placeholder',
      type: 'text',
    },
  ];

  private readonly translationChange = toSignal(
    this.t.onTranslationChange.pipe(startWith(null)),
  );

  // --- Overview query (KPI cards) ---

  readonly overviewQuery = injectQuery(() => ({
    queryKey: ['analytics', 'inventory-overview', this.wsStore.currentWorkspaceId()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getInventoryOverview(
          this.wsStore.currentWorkspaceId()!,
          {},
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  private readonly overview = computed(() => this.overviewQuery.data() ?? null);

  readonly kpiTotalSkus = computed(() => {
    const data = this.overview();
    return data ? data.totalSkus.toLocaleString('ru-RU') : null;
  });

  readonly kpiCritical = computed(() => {
    const data = this.overview();
    return data ? data.criticalCount.toLocaleString('ru-RU') : null;
  });

  readonly kpiWarning = computed(() => {
    const data = this.overview();
    return data ? data.warningCount.toLocaleString('ru-RU') : null;
  });

  readonly kpiFrozenCapital = computed(() => {
    const data = this.overview();
    return data ? formatMoney(data.frozenCapital, 0) : null;
  });

  // --- Grid filter ---

  private readonly filter = computed<AnalyticsFilter>(() => {
    const vals = this.listState.filterValues();
    const f: AnalyticsFilter = {};
    if (vals['stockOutRisk']?.length) {
      f.stockOutRisk = vals['stockOutRisk'].join(',');
    }
    if (vals['sourcePlatform']?.length) {
      f.sourcePlatform = vals['sourcePlatform'].join(',');
    }
    if (vals['search']) f.search = vals['search'];
    return f;
  });

  // --- Products grid query ---

  readonly productsQuery = injectQuery(() => ({
    queryKey: [
      'analytics', 'inventory-by-product',
      this.wsStore.currentWorkspaceId(),
      this.filter(),
      this.listState.currentPage(),
      this.listState.pageSize(),
      this.listState.sortParam(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.listInventoryByProduct(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.listState.currentPage(),
          this.listState.pageSize(),
          this.listState.sortParam(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly gridRows = computed(() => this.productsQuery.data()?.content ?? []);
  readonly totalElements = computed(() => this.productsQuery.data()?.totalElements ?? 0);

  // --- Detail panel history query ---

  readonly detailHistoryQuery = injectQuery(() => ({
    queryKey: [
      'analytics', 'inventory-detail-history',
      this.wsStore.currentWorkspaceId(),
      this.selectedProduct()?.productId,
    ],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getStockHistory(
          this.wsStore.currentWorkspaceId()!,
          daysAgo(30),
          daysAgo(0),
          this.selectedProduct()?.productId,
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !!this.selectedProduct(),
    staleTime: 60_000,
  }));

  // --- Column definitions ---

  readonly columnDefs = computed<ColDef[]>(() => {
    this.translationChange();
    return [
      {
        field: 'skuCode',
        headerName: 'SKU',
        minWidth: 120,
        pinned: 'left' as const,
        sortable: true,
        cellClass: 'font-mono text-[11px]',
        cellRenderer: (params: any) => {
          if (!params.value) return '';
          return `<span class="font-medium text-[var(--accent-primary)] cursor-pointer hover:underline">${params.value}</span>`;
        },
        onCellClicked: (params: any) => {
          if (params.data) this.selectProduct(params.data);
        },
      },
      {
        field: 'productName',
        headerName: this.t.instant('analytics.inventory.col.product'),
        minWidth: 220,
        flex: 1,
        sortable: true,
        tooltipField: 'productName',
      },
      platformColumn(this.t, 'sourcePlatform', 'analytics.inventory.col.platform'),
      {
        field: 'available',
        headerName: this.t.instant('analytics.inventory.col.available'),
        width: 100,
        sortable: true,
        cellClass: 'font-mono text-right',
      },
      {
        field: 'daysOfCover',
        headerName: this.t.instant('analytics.inventory.col.days_of_cover'),
        width: 110,
        sortable: true,
        cellClass: 'font-mono text-right',
        valueFormatter: (p: any) => p.value ?? '—',
      },
      {
        field: 'stockOutRisk',
        headerName: this.t.instant('analytics.inventory.col.risk'),
        width: 110,
        sortable: true,
        cellRenderer: (p: { value: string }) => {
          if (!p.value) return '—';
          let dotCls: string;
          switch (p.value) {
            case 'CRITICAL': dotCls = 'bg-[var(--status-error)]'; break;
            case 'WARNING': dotCls = 'bg-[var(--status-warning)]'; break;
            default: dotCls = 'bg-[var(--status-success)]'; break;
          }
          const label = this.t.instant(`analytics.inventory.risk.${p.value.toLowerCase()}`);
          return `<span class="inline-flex items-center gap-1 text-[11px]"><span class="inline-block h-1.5 w-1.5 rounded-full ${dotCls}"></span>${label}</span>`;
        },
      },
      {
        field: 'frozenCapital',
        headerName: this.t.instant('analytics.inventory.col.frozen_capital'),
        width: 140,
        sortable: true,
        cellClass: 'font-mono text-right',
        valueFormatter: (p: any) => formatMoney(p.value, 0),
      },
      {
        field: 'recommendedReplenishment',
        headerName: this.t.instant('analytics.inventory.col.replenishment'),
        width: 120,
        sortable: true,
        cellClass: 'font-mono text-right',
        valueFormatter: (p: any) => p.value ?? '—',
      },
    ];
  });

  // --- Detail chart ---

  readonly detailChartOptions = computed<EChartsOption>(() => {
    const points = this.detailHistoryQuery.data() ?? [];
    const dates = points.map((p) => p.date);
    const availableData = points.map((p) => p.available);

    const accentColor = resolveCssVar('--accent-primary');
    const accentSubtle = resolveCssVar('--accent-subtle');

    return {
      grid: { left: 40, right: 8, top: 8, bottom: 24 },
      xAxis: {
        type: 'category',
        data: dates,
        axisLabel: { color: resolveCssVar('--text-tertiary'), fontSize: 10, rotate: 30 },
        axisLine: { lineStyle: { color: resolveCssVar('--border-subtle') } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: resolveCssVar('--text-tertiary'), fontSize: 10 },
        splitLine: { lineStyle: { color: resolveCssVar('--border-subtle') } },
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: resolveCssVar('--bg-primary'),
        borderColor: resolveCssVar('--border-default'),
        textStyle: { color: resolveCssVar('--text-primary'), fontSize: 11 },
      },
      series: [
        {
          type: 'line',
          step: 'end',
          data: availableData,
          lineStyle: { color: accentColor, width: 1.5 },
          itemStyle: { color: accentColor },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: accentSubtle },
                { offset: 1, color: 'transparent' },
              ],
            } as Record<string, unknown>,
          },
          symbol: 'none',
        },
      ],
    };
  });

  // --- Actions ---

  readonly getRowId = (params: any) =>
    `${params.data.productId}-${params.data.warehouseId ?? 0}`;

  onGridReady(api: GridApi): void {
    this.gridApi = api;
  }

  onRowClicked(event: any): void {
    if (event?.data) {
      this.selectProduct(event.data);
    }
  }

  exportCsv(): void {
    this.gridApi?.exportDataAsCsv({ fileName: 'inventory-by-product.csv' });
  }

  selectProduct(product: InventoryByProduct): void {
    this.selectedProduct.set(
      this.selectedProduct()?.productId === product.productId ? null : product,
    );
  }

  toggleRiskFilter(risk: StockOutRisk): void {
    const current = this.kpiRiskFilter();
    if (current === risk) {
      this.kpiRiskFilter.set(null);
      const vals = { ...this.listState.filterValues() };
      delete vals['stockOutRisk'];
      this.listState.onFiltersChanged(vals);
    } else {
      this.kpiRiskFilter.set(risk);
      this.listState.onFiltersChanged({
        ...this.listState.filterValues(),
        stockOutRisk: [risk],
      });
    }
  }

  fmtMoney(value: number | null): string {
    return formatMoney(value, 0);
  }
}
