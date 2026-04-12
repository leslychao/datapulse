import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { ColDef, GridApi } from 'ag-grid-community';
import { LucideAngularModule, Download } from 'lucide-angular';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { MonthPickerComponent } from '@shared/components/form/month-picker.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { PaginationBarComponent } from '@shared/components/pagination-bar/pagination-bar.component';
import { NavigationStore } from '@shared/stores/navigation.store';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { createDebouncedSearch } from '@shared/utils/debounced-search';
import { formatMoney, financeColor, currentMonth } from '@shared/utils/format.utils';
import { platformColumn } from '@shared/utils/column-factories';
import {
  UrlFilterDef, isFiltersDefault, resetFilters, initPersistedFilters,
} from '@shared/utils/url-filters';

const COGS_STATUS_KEY: Record<string, string> = {
  OK: 'analytics.pnl.cogs_status.OK',
  NO_COST_PROFILE: 'analytics.pnl.cogs_status.NO_COST_PROFILE',
  NO_SALES: 'analytics.pnl.cogs_status.NO_SALES',
};

const COGS_STATUS_COLOR: Record<string, string> = {
  OK: 'bg-[var(--status-success-bg)] text-[var(--status-success)]',
  NO_COST_PROFILE: 'bg-[var(--status-warning-bg)] text-[var(--status-warning)]',
  NO_SALES: 'bg-[var(--status-neutral-bg)] text-[var(--status-neutral)]',
};

@Component({
  selector: 'dp-pnl-by-product-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    MonthPickerComponent,
    DataGridComponent,
    LucideAngularModule,
    PaginationBarComponent,
  ],
  template: `
    <div class="flex flex-col gap-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3">
        <dp-month-picker [value]="period()" (valueChange)="onPeriodChange($event)" />
        <input
          type="text"
          [value]="search()"
          (input)="onSearchInput($event)"
          [placeholder]="'analytics.pnl.search_placeholder' | translate"
          class="w-64 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]
                 px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-primary)]
                 outline-none focus:border-[var(--accent-primary)]"
        />
        @if (!filtersDefault()) {
          <button type="button" (click)="onResetFilters()"
            class="h-8 cursor-pointer rounded-[var(--radius-md)] px-3 text-[length:var(--text-sm)]
                   text-[var(--text-tertiary)] transition-colors
                   hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]">
            {{ 'filter_bar.reset_all' | translate }}
          </button>
        }
        <button
          (click)="exportCsv()"
          class="ml-auto flex items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
        >
          <lucide-icon [img]="downloadIcon" size="14" />
          <span>{{ 'common.export_csv' | translate }}</span>
        </button>
      </div>

      <p class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
        {{ 'analytics.pnl.note.financial_refunds_scope' | translate }}
      </p>

      <dp-data-grid
        viewStateKey="analytics:pnl:by-product"
        [columnDefs]="columnDefs()"
        [rowData]="gridRows()"
        [loading]="productsQuery.isPending()"
        [pagination]="false"
        [pageSize]="50"
        height="calc(100vh - 320px)"
        (gridReady)="onGridReady($event)"
      />

      <dp-pagination-bar
        [totalItems]="productsQuery.data()?.totalElements ?? 0"
        [pageSize]="pageSize()"
        [currentPage]="currentPage()"
        [pageSizeOptions]="[25, 50, 100]"
        (pageChange)="onPageChange($event)"
      />
    </div>
  `,
})
export class PnlByProductPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly navStore = inject(NavigationStore);
  private readonly t = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly downloadIcon = Download;
  readonly period = signal(
    this.navStore.getSectionFilterValue<string>('analytics:pnl', 'period') ?? currentMonth(),
  );
  readonly search = signal('');
  readonly currentPage = signal(0);
  readonly pageSize = signal(50);

  private readonly filterDefs: UrlFilterDef[] = [
    { key: 'period', signal: this.period, defaultValue: currentMonth() },
    { key: 'search', signal: this.search, defaultValue: '' },
  ];
  readonly filtersDefault = isFiltersDefault(this.filterDefs);

  private gridApi: GridApi | null = null;

  readonly onSearchInput = createDebouncedSearch(this.search, 300, () => this.currentPage.set(0));

  constructor() {
    effect(() => {
      this.navStore.setSectionFilter('analytics:pnl', { period: this.period() });
    });
    initPersistedFilters(this.router, this.route, {
      pageKey: 'analytics:pnl:by-product', filterDefs: this.filterDefs,
    });
  }

  onResetFilters(): void {
    resetFilters(this.filterDefs);
    this.currentPage.set(0);
  }

  readonly productsQuery = injectQuery(() => ({
    queryKey: [
      'analytics', 'pnl-by-product',
      this.wsStore.currentWorkspaceId(),
      this.period(),
      this.search(),
      this.currentPage(),
      this.pageSize(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.listPnlByProduct(
          this.wsStore.currentWorkspaceId()!,
          { period: this.period(), search: this.search() || undefined },
          this.currentPage(),
          this.pageSize(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly gridRows = computed(() => this.productsQuery.data()?.content ?? []);

  readonly columnDefs = computed<ColDef[]>(() => [
    {
      field: 'skuCode',
      headerName: this.t.instant('analytics.pnl.col.sku'),
      cellClass: 'font-mono text-[11px]',
    },
    {
      field: 'productName',
      headerName: this.t.instant('analytics.pnl.col.product'),
      minWidth: 200,
    },
    platformColumn(this.t),
    {
      field: 'revenueAmount',
      headerName: this.t.instant('analytics.pnl.col.revenue'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
      cellStyle: (p) => ({ color: financeColor(p.value) }),
    },
    {
      field: 'marketplaceCommissionAmount',
      headerName: this.t.instant('analytics.pnl.col.commission'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
      cellStyle: () => ({ color: 'var(--finance-negative)' }),
    },
    {
      field: 'acquiringCommissionAmount',
      headerName: this.t.instant('analytics.pnl.col.acquiring'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
      cellStyle: () => ({ color: 'var(--finance-negative)' }),
    },
    {
      field: 'logisticsCostAmount',
      headerName: this.t.instant('analytics.pnl.col.logistics'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
      cellStyle: () => ({ color: 'var(--finance-negative)' }),
    },
    {
      field: 'penaltiesAmount',
      headerName: this.t.instant('analytics.pnl.col.penalties'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
      cellStyle: () => ({ color: 'var(--finance-negative)' }),
    },
    {
      field: 'refundAmount',
      headerName: this.t.instant('analytics.pnl.col.refunds'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
      cellStyle: () => ({ color: 'var(--finance-negative)' }),
    },
    {
      field: 'netCogs',
      headerName: this.t.instant('analytics.pnl.col.cogs'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
      cellStyle: () => ({ color: 'var(--finance-negative)' }),
    },
    {
      field: 'fullPnl',
      headerName: this.t.instant('analytics.pnl.col.full_pnl'),
      type: 'rightAligned',
      cellClass: 'font-mono font-semibold',
      valueFormatter: (p) => formatMoney(p.value, 0),
      cellStyle: (p) => ({ color: financeColor(p.value) }),
    },
    {
      field: 'cogsStatus',
      headerName: this.t.instant('analytics.pnl.col.cogs_status'),
      cellRenderer: (p: { value: string }) => {
        const key = COGS_STATUS_KEY[p.value];
        const label = key ? this.t.instant(key) : p.value;
        const cls = COGS_STATUS_COLOR[p.value] ?? COGS_STATUS_COLOR['NO_SALES'];
        return `<span class="rounded-[var(--radius-sm)] px-1.5 py-0.5 text-[11px] font-medium ${cls}">${label}</span>`;
      },
    },
  ]);

  onGridReady(api: GridApi): void {
    this.gridApi = api;
  }

  exportCsv(): void {
    this.gridApi?.exportDataAsCsv({ fileName: 'pnl-by-product.csv' });
  }

  onPeriodChange(value: string): void {
    this.period.set(value);
    this.currentPage.set(0);
  }

  onPageChange(event: { page: number; pageSize: number }): void {
    this.currentPage.set(event.page);
    this.pageSize.set(event.pageSize);
  }
}
