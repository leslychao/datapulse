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
import { PnlByPosting } from '@core/models';
import { MonthPickerComponent } from '@shared/components/form/month-picker.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { PaginationBarComponent } from '@shared/components/pagination-bar/pagination-bar.component';
import { NavigationStore } from '@shared/stores/navigation.store';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { createDebouncedSearch } from '@shared/utils/debounced-search';
import {
  formatMoney,
  financeColor,
  formatDateTime,
  currentMonth,
} from '@shared/utils/format.utils';
import {
  UrlFilterDef, readFiltersFromUrl, syncFiltersToUrl, isFiltersDefault, resetFilters,
} from '@shared/utils/url-filters';

function monthStart(period: string): string {
  return `${period}-01`;
}

function monthEnd(period: string): string {
  const [y, m] = period.split('-').map(Number);
  const last = new Date(y, m, 0).getDate();
  return `${period}-${String(last).padStart(2, '0')}`;
}

@Component({
  selector: 'dp-pnl-by-posting-page',
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
    <div class="flex flex-col overflow-hidden gap-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3">
        <dp-month-picker [value]="period()" (valueChange)="onPeriodChange($event)" />
        <input
          type="text"
          [value]="search()"
          (input)="onSearchInput($event)"
          [placeholder]="'analytics.pnl.search_sku' | translate"
          class="w-52 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]
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

      <dp-data-grid
        [columnDefs]="columnDefs()"
        [rowData]="gridRows()"
        [loading]="postingsQuery.isPending()"
        [pagination]="false"
        [pageSize]="50"
        height="calc(100vh - 320px)"
        (gridReady)="onGridReady($event)"
      />

      <dp-pagination-bar
        [totalItems]="postingsQuery.data()?.totalElements ?? 0"
        [pageSize]="pageSize()"
        [currentPage]="currentPage()"
        [pageSizeOptions]="[25, 50, 100]"
        (pageChange)="onPageChange($event)"
      />
    </div>
  `,
})
export class PnlByPostingPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly navStore = inject(NavigationStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly t = inject(TranslateService);

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

  constructor() {
    readFiltersFromUrl(this.route, this.filterDefs);
    syncFiltersToUrl(this.router, this.route, this.filterDefs);
    effect(() => {
      this.navStore.setSectionFilter('analytics:pnl', { period: this.period() });
    });
  }

  onResetFilters(): void {
    resetFilters(this.filterDefs);
    this.currentPage.set(0);
  }

  readonly postingsQuery = injectQuery(() => ({
    queryKey: [
      'analytics', 'pnl-by-posting',
      this.wsStore.currentWorkspaceId(),
      this.period(),
      this.search(),
      this.currentPage(),
      this.pageSize(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.listPnlByPosting(
          this.wsStore.currentWorkspaceId()!,
          {
            from: monthStart(this.period()),
            to: monthEnd(this.period()),
            search: this.search() || undefined,
          },
          this.currentPage(),
          this.pageSize(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly gridRows = computed(() => this.postingsQuery.data()?.content ?? []);

  readonly columnDefs = computed<ColDef[]>(() => [
    {
      field: 'postingId',
      headerName: this.t.instant('analytics.pnl.col.posting_id'),
      cellClass: 'font-mono text-[11px]',
      cellRenderer: (params: any) => {
        if (!params.value) return '';
        return `<span class="text-[var(--accent-primary)] cursor-pointer hover:underline">${params.value}</span>`;
      },
      onCellClicked: (params: any) => {
        if (params.data) this.onRowClicked(params.data);
      },
    },
    {
      field: 'skuCode',
      headerName: this.t.instant('analytics.pnl.col.sku'),
      cellClass: 'font-mono text-[11px]',
    },
    {
      field: 'productName',
      headerName: this.t.instant('analytics.pnl.col.product'),
      minWidth: 180,
    },
    {
      field: 'financeDate',
      headerName: this.t.instant('analytics.pnl.col.date'),
      valueFormatter: (p) => formatDateTime(p.value),
      cellStyle: () => ({ color: 'var(--text-secondary)' }),
    },
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
      field: 'netPayout',
      headerName: this.t.instant('analytics.pnl.col.payout'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
      cellStyle: (p) => ({ color: financeColor(p.value) }),
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
      field: 'reconciliationResidual',
      headerName: this.t.instant('analytics.pnl.col.residual'),
      type: 'rightAligned',
      cellClass: 'font-mono font-semibold',
      valueFormatter: (p) => formatMoney(p.value, 0),
      cellStyle: (p) => ({
        color: p.value !== 0 ? 'var(--status-warning)' : 'var(--finance-zero)',
      }),
    },
  ]);

  onGridReady(api: GridApi): void {
    this.gridApi = api;
  }

  exportCsv(): void {
    this.gridApi?.exportDataAsCsv({ fileName: 'pnl-by-posting.csv' });
  }

  onPeriodChange(value: string): void {
    this.period.set(value);
    this.currentPage.set(0);
  }

  readonly onSearchInput = createDebouncedSearch(this.search, 300, () => this.currentPage.set(0));

  onRowClicked(row: PnlByPosting): void {
    this.router.navigate([
      '/workspace', this.wsStore.currentWorkspaceId(),
      'analytics', 'pnl', 'posting', row.postingId,
    ]);
  }

  onPageChange(event: { page: number; pageSize: number }): void {
    this.currentPage.set(event.page);
    this.pageSize.set(event.pageSize);
  }
}
