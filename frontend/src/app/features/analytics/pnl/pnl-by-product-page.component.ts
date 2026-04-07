import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { ColDef, GridApi } from 'ag-grid-community';
import { LucideAngularModule, Download } from 'lucide-angular';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { MonthPickerComponent } from '@shared/components/form/month-picker.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { formatMoney, financeColor, currentMonth } from '@shared/utils/format.utils';

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
  imports: [TranslatePipe, MonthPickerComponent, DataGridComponent, LucideAngularModule],
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
        [loading]="productsQuery.isPending()"
        [pagination]="false"
        [pageSize]="50"
        height="calc(100vh - 320px)"
        [clickableRows]="true"
        (rowClicked)="onRowClicked($event)"
        (gridReady)="onGridReady($event)"
      />

      @if (productsQuery.data(); as page) {
        <div class="flex items-center justify-between text-[length:var(--text-sm)] text-[var(--text-secondary)]">
          <span>
            {{ 'pagination.showing' | translate:{
              from: page.number * page.size + 1,
              to: page.number * page.size + page.content.length,
              total: page.totalElements
            } }}
          </span>
          <div class="flex items-center gap-2">
            <button
              (click)="prevPage()"
              [disabled]="currentPage() === 0"
              class="rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1
                     text-[length:var(--text-sm)] transition-colors hover:bg-[var(--bg-tertiary)]
                     disabled:cursor-not-allowed disabled:opacity-40"
            >
              {{ 'pagination.prev' | translate }}
            </button>
            <button
              (click)="nextPage()"
              [disabled]="currentPage() >= page.totalPages - 1"
              class="rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1
                     text-[length:var(--text-sm)] transition-colors hover:bg-[var(--bg-tertiary)]
                     disabled:cursor-not-allowed disabled:opacity-40"
            >
              {{ 'pagination.next' | translate }}
            </button>
          </div>
        </div>
      }
    </div>
  `,
})
export class PnlByProductPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly t = inject(TranslateService);

  readonly downloadIcon = Download;
  readonly period = signal(currentMonth());
  readonly search = signal('');
  readonly currentPage = signal(0);
  readonly pageSize = signal(50);

  private gridApi: GridApi | null = null;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

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
    {
      field: 'sourcePlatform',
      headerName: this.t.instant('analytics.pnl.col.platform'),
      cellRenderer: (p: { value: string }) => {
        const cls = p.value === 'WB'
          ? 'bg-[var(--mp-wb-bg)] text-[var(--mp-wb)]'
          : p.value === 'OZON'
            ? 'bg-[var(--mp-ozon-bg)] text-[var(--mp-ozon)]'
            : 'bg-[var(--status-neutral-bg)] text-[var(--status-neutral)]';
        return `<span class="rounded-[var(--radius-sm)] px-1.5 py-0.5 text-[11px] font-medium ${cls}">${p.value}</span>`;
      },
    },
    {
      field: 'revenueAmount',
      headerName: this.t.instant('analytics.pnl.col.revenue'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
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
      field: 'logisticsCostAmount',
      headerName: this.t.instant('analytics.pnl.col.logistics'),
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

  onSearchInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.search.set(input.value);
      this.currentPage.set(0);
    }, 300);
  }

  onRowClicked(_row: any): void {
    // Future detail panel
  }

  prevPage(): void {
    this.currentPage.update((p) => Math.max(0, p - 1));
  }

  nextPage(): void {
    this.currentPage.update((p) => p + 1);
  }
}
