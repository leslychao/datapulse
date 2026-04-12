import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  HostListener,
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
import { ReturnsByProduct, MARKETPLACE_REGISTRY, getMarketplaceShortLabel } from '@core/models';
import { MonthPickerComponent } from '@shared/components/form/month-picker.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { PaginationBarComponent } from '@shared/components/pagination-bar/pagination-bar.component';
import { NavigationStore } from '@shared/stores/navigation.store';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { createDebouncedSearch } from '@shared/utils/debounced-search';
import {
  formatMoney,
  formatPercent,
  currentMonth,
} from '@shared/utils/format.utils';
import { platformColumn } from '@shared/utils/column-factories';
import {
  UrlFilterDef, isFiltersDefault, resetFilters, initPersistedFilters,
  SortUrlState,
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
  selector: 'dp-returns-by-product-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, DataGridComponent, LucideAngularModule, MonthPickerComponent, PaginationBarComponent],
  template: `
    <div class="flex h-full flex-col gap-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3">
        <dp-month-picker [value]="period()" (valueChange)="onPeriodChange($event)" />
        <select
          [value]="platform()"
          (change)="platform.set($any($event.target).value)"
          class="h-8 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]
                 px-2.5 text-[length:var(--text-sm)] text-[var(--text-primary)]
                 outline-none focus:border-[var(--accent-primary)]">
          <option value="">{{ 'analytics.returns.filter.all_platforms' | translate }}</option>
          @for (mp of marketplaces; track mp.type) {
            <option [value]="mp.type">{{ mp.label }}</option>
          }
        </select>
        <input
          type="text"
          [value]="search()"
          (input)="onSearchInput($event)"
          [placeholder]="'analytics.returns.search_placeholder' | translate"
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

      @if (!returnsQuery.isPending() && gridRows().length === 0) {
        <div class="rounded-[var(--radius-md)] border border-[var(--border-default)]
                    bg-[var(--bg-secondary)] px-4 py-8 text-center">
          <p class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'analytics.returns.by_product.empty' | translate }}
          </p>
        </div>
      } @else {
        <div class="flex-1">
          <dp-data-grid
            viewStateKey="analytics:returns:by-product"
            [columnDefs]="columnDefs()"
            [rowData]="gridRows()"
            [loading]="returnsQuery.isPending()"
            [pagination]="false"
            [pageSize]="50"
            height="calc(100vh - 320px)"
            [initialSortModel]="initialSortModel()"
            (sortChanged)="onSortChanged($event)"
            (gridReady)="onGridReady($event)"
          />
        </div>
      }

      @if (returnsQuery.data(); as page) {
        <dp-pagination-bar
          [totalItems]="page.totalElements"
          [pageSize]="pageSize()"
          [currentPage]="currentPage()"
          (pageChange)="onPageChange($event)"
        />
      }

      <!-- Detail Panel -->
      @if (selectedProduct()) {
        <div
          class="fixed inset-y-0 right-0 z-40 flex w-[420px] flex-col border-l border-[var(--border-default)] bg-[var(--bg-primary)] shadow-lg"
        >
          <div class="flex items-center justify-between border-b border-[var(--border-default)] px-4 py-3">
            <h3 class="text-sm font-medium text-[var(--text-primary)]">
              {{ selectedProduct()!.productName }}
            </h3>
            <button
              (click)="selectedProduct.set(null)"
              class="cursor-pointer rounded-[var(--radius-sm)] p-1 text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
              [attr.aria-label]="'common.close' | translate"
            >
              ✕
            </button>
          </div>

          <div class="flex-1 space-y-4 overflow-y-auto p-4">
            <div class="text-xs text-[var(--text-tertiary)]">
              {{ selectedProduct()!.skuCode }} · {{ mpShortLabel(selectedProduct()!.sourcePlatform) }}
            </div>

            <!-- Return Stats -->
            <section>
              <h4 class="mb-2 text-xs font-medium uppercase tracking-wide text-[var(--text-tertiary)]">
                {{ 'analytics.returns.detail.stats' | translate }}
              </h4>
              <div class="grid grid-cols-2 gap-3">
                <div class="rounded-[var(--radius-sm)] bg-[var(--bg-secondary)] p-3">
                  <div class="text-[11px] text-[var(--text-tertiary)]">{{ 'analytics.returns.detail.returns_count' | translate }}</div>
                  <div class="font-mono text-lg font-bold text-[var(--text-primary)]">
                    {{ selectedProduct()!.returnCount }}
                  </div>
                </div>
                <div class="rounded-[var(--radius-sm)] bg-[var(--bg-secondary)] p-3">
                  <div class="text-[11px] text-[var(--text-tertiary)]">{{ 'analytics.returns.detail.quantity' | translate }}</div>
                  <div class="font-mono text-lg font-bold text-[var(--text-primary)]">
                    {{ selectedProduct()!.returnQuantity }}
                  </div>
                </div>
                <div class="rounded-[var(--radius-sm)] bg-[var(--bg-secondary)] p-3">
                  <div class="text-[11px] text-[var(--text-tertiary)]">{{ 'analytics.returns.detail.return_rate_pct' | translate }}</div>
                  <div class="font-mono text-lg font-bold" [class]="returnRateClass(selectedProduct()!.returnRatePct)">
                    {{ formatPct(selectedProduct()!.returnRatePct) }}
                  </div>
                </div>
                <div class="rounded-[var(--radius-sm)] bg-[var(--bg-secondary)] p-3">
                  <div class="text-[11px] text-[var(--text-tertiary)]">{{ 'analytics.returns.detail.return_amount' | translate }}</div>
                  <div class="font-mono text-lg font-bold text-[var(--text-primary)]">
                    {{ formatMoney(selectedProduct()!.returnAmount) }}
                  </div>
                </div>
              </div>
            </section>

            <!-- Sales Context -->
            <section>
              <h4 class="mb-2 text-xs font-medium uppercase tracking-wide text-[var(--text-tertiary)]">
                {{ 'analytics.returns.detail.sales_context' | translate }}
              </h4>
              <div class="space-y-2">
                <div class="flex items-center justify-between rounded-[var(--radius-sm)] bg-[var(--bg-secondary)] p-3">
                  <span class="text-sm text-[var(--text-secondary)]">{{ 'analytics.returns.detail.sales' | translate }}</span>
                  <span class="font-mono text-sm text-[var(--text-primary)]">
                    {{ selectedProduct()!.saleCount }} / {{ selectedProduct()!.saleQuantity }} {{ 'common.pcs' | translate }}
                  </span>
                </div>
                <div class="flex items-center justify-between rounded-[var(--radius-sm)] bg-[var(--bg-secondary)] p-3">
                  <span class="text-sm text-[var(--text-secondary)]">{{ 'analytics.returns.detail.return_amount' | translate }}</span>
                  <span class="font-mono text-sm text-[var(--text-primary)]">
                    {{ formatMoney(selectedProduct()!.returnAmount) }}
                  </span>
                </div>
              </div>
            </section>

            <!-- Reason info -->
            <section>
              <h4 class="mb-2 text-xs font-medium uppercase tracking-wide text-[var(--text-tertiary)]">
                {{ 'analytics.returns.detail.reason' | translate }}
              </h4>
              <div class="rounded-[var(--radius-sm)] bg-[var(--bg-secondary)] p-3">
                <div class="text-sm text-[var(--text-primary)]">
                  {{ selectedProduct()!.topReturnReason || '—' }}
                </div>
                @if (selectedProduct()!.distinctReasonCount > 1) {
                  <div class="mt-1 text-[11px] text-[var(--text-tertiary)]">
                    {{ 'analytics.returns.detail.reason_count' | translate:{ count: selectedProduct()!.distinctReasonCount } }}
                  </div>
                }
              </div>
            </section>
          </div>
        </div>
      }
    </div>
  `,
})
export class ReturnsByProductPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly navStore = inject(NavigationStore);
  private readonly t = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly downloadIcon = Download;
  protected readonly marketplaces = MARKETPLACE_REGISTRY;
  protected readonly mpShortLabel = getMarketplaceShortLabel;
  private gridApi: GridApi | null = null;

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.selectedProduct.set(null);
  }

  readonly period = signal(
    this.navStore.getSectionFilterValue<string>('analytics:returns', 'period') ?? currentMonth()
  );
  readonly platform = signal(
    this.navStore.getSectionFilterValue<string>('analytics:returns', 'platform') ?? ''
  );
  readonly search = signal('');
  readonly selectedProduct = signal<ReturnsByProduct | null>(null);
  readonly currentPage = signal(0);
  readonly currentSort = signal<SortUrlState>({ column: 'returnRatePct', direction: 'desc' });
  readonly sortString = computed(() => `${this.currentSort().column},${this.currentSort().direction}`);
  readonly initialSortModel = computed(() => {
    const s = this.currentSort();
    return s.column ? [{ colId: s.column, sort: s.direction }] : [];
  });
  readonly pageSize = signal(50);

  private readonly filterDefs: UrlFilterDef[] = [
    { key: 'period', signal: this.period, defaultValue: currentMonth() },
    { key: 'platform', signal: this.platform, defaultValue: '' },
    { key: 'search', signal: this.search, defaultValue: '' },
  ];
  readonly filtersDefault = isFiltersDefault(this.filterDefs);

  readonly onSearchInput = createDebouncedSearch(this.search, 300, () => this.currentPage.set(0));

  constructor() {
    initPersistedFilters(this.router, this.route, {
      pageKey: 'analytics:returns:by-product',
      filterDefs: this.filterDefs,
      sortSignal: this.currentSort,
      defaultSort: { column: 'returnRatePct', direction: 'desc' },
    });
    effect(() => {
      const period = this.period();
      const platform = this.platform();
      this.navStore.setSectionFilter('analytics:returns', {
        period, platform,
        from: monthStart(period),
        to: monthEnd(period),
      });
    });
  }

  onResetFilters(): void {
    resetFilters(this.filterDefs);
    this.currentPage.set(0);
  }

  readonly returnsQuery = injectQuery(() => ({
    queryKey: [
      'analytics', 'returns-by-product',
      this.wsStore.currentWorkspaceId(),
      this.period(),
      this.platform(),
      this.search(),
      this.currentPage(),
      this.sortString(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.listReturnsByProduct(
          this.wsStore.currentWorkspaceId()!,
          { period: this.period(), search: this.search() || undefined,
            sourcePlatform: this.platform() || undefined },
          this.currentPage(),
          this.pageSize(),
          this.sortString(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly gridRows = computed(() => this.returnsQuery.data()?.content ?? []);

  readonly columnDefs = computed<ColDef[]>(() => [
    {
      field: 'skuCode',
      headerName: 'SKU',
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
      field: 'productName',
      headerName: this.t.instant('analytics.returns.col.product'),
      minWidth: 200,
    },
    platformColumn(this.t, 'sourcePlatform', 'analytics.returns.col.platform'),
    {
      field: 'returnCount',
      headerName: this.t.instant('analytics.returns.col.return_count'),
      type: 'rightAligned',
      cellClass: 'font-mono',
    },
    {
      field: 'returnRatePct',
      headerName: this.t.instant('analytics.returns.col.return_rate'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatPercent(p.value),
      cellStyle: (p) => {
        if (p.value > 10) return { color: 'var(--status-error)' };
        if (p.value >= 5) return { color: 'var(--status-warning)' };
        return { color: 'var(--text-primary)' };
      },
    },
    {
      field: 'returnQuantity',
      headerName: this.t.instant('analytics.returns.col.return_quantity'),
      type: 'rightAligned',
      cellClass: 'font-mono',
    },
    {
      field: 'saleQuantity',
      headerName: this.t.instant('analytics.returns.col.sale_quantity'),
      type: 'rightAligned',
      cellClass: 'font-mono',
    },
    {
      field: 'returnAmount',
      headerName: this.t.instant('analytics.returns.col.return_amount'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
    },
    {
      field: 'topReturnReason',
      headerName: this.t.instant('analytics.returns.col.top_reason'),
      cellRenderer: (p: any) => {
        if (!p.value) return '—';
        const cnt = p.data?.distinctReasonCount ?? 1;
        const badge = cnt > 1
          ? ` <span class="ml-1 rounded-[var(--radius-sm)] bg-[var(--bg-tertiary)] px-1 py-0.5 text-[10px] text-[var(--text-tertiary)]">+${cnt - 1}</span>`
          : '';
        return `<span>${p.value}${badge}</span>`;
      },
    },
  ]);

  onGridReady(api: GridApi): void {
    this.gridApi = api;
  }

  exportCsv(): void {
    this.gridApi?.exportDataAsCsv({ fileName: 'returns-by-product.csv' });
  }

  onPeriodChange(value: string): void {
    this.period.set(value);
    this.currentPage.set(0);
  }

  onPageChange(event: { page: number; pageSize: number }): void {
    this.currentPage.set(event.page);
    this.pageSize.set(event.pageSize);
  }

  onRowClicked(row: ReturnsByProduct): void {
    this.selectedProduct.set(row);
  }

  onSortChanged(sort: { column: string; direction: string }): void {
    if (sort.column) {
      this.currentSort.set({ column: sort.column, direction: sort.direction as 'asc' | 'desc' });
    } else {
      this.currentSort.set({ column: 'returnRatePct', direction: 'desc' });
    }
    this.currentPage.set(0);
  }

  returnRateClass(rate: number): string {
    if (rate > 10) return 'text-[var(--status-error)]';
    if (rate >= 5) return 'text-[var(--status-warning)]';
    return 'text-[var(--text-primary)]';
  }

  formatMoney(value: number | null): string {
    return formatMoney(value, 0);
  }

  formatPct(value: number | null): string {
    return formatPercent(value);
  }
}
