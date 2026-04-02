import {
  ChangeDetectionStrategy,
  Component,
  computed,
  HostListener,
  inject,
  signal,
} from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { ColDef, GridApi } from 'ag-grid-community';
import { LucideAngularModule, Download } from 'lucide-angular';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import {
  AnalyticsFilter,
  InventoryByProduct,
  StockOutRisk,
} from '@core/models';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { formatMoney } from '@shared/utils/format.utils';

@Component({
  selector: 'dp-inventory-by-product-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, DataGridComponent, LucideAngularModule],
  template: `
    <div class="flex h-full">
      <!-- Main content -->
      <div class="flex flex-1 flex-col overflow-hidden">
        <!-- Filter bar -->
        <div class="flex items-center gap-3 border-b border-[var(--border-default)] bg-[var(--bg-secondary)] py-2.5">
          <select
            class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
            [value]="connectionId()"
            (change)="onConnectionChange($event)"
          >
            <option [value]="0">{{ 'analytics.filter.all_connections' | translate }}</option>
          </select>

          <select
            class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
            [value]="riskFilter()"
            (change)="onRiskFilterChange($event)"
          >
            <option value="">{{ 'analytics.inventory.filter.all_risks' | translate }}</option>
            <option value="CRITICAL">{{ 'analytics.inventory.filter.critical' | translate }}</option>
            <option value="WARNING">{{ 'analytics.inventory.filter.warning' | translate }}</option>
            <option value="NORMAL">{{ 'analytics.inventory.filter.normal' | translate }}</option>
          </select>

          <input
            type="text"
            class="w-56 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-sm text-[var(--text-primary)] outline-none placeholder:text-[var(--text-tertiary)] focus:border-[var(--accent-primary)]"
            [placeholder]="'analytics.inventory.filter.search_placeholder' | translate"
            [value]="searchTerm()"
            (input)="onSearchInput($event)"
          />
          <button
            (click)="exportCsv()"
            class="ml-auto flex items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
          >
            <lucide-icon [img]="downloadIcon" size="14" />
            <span>{{ 'common.export_csv' | translate }}</span>
          </button>
        </div>

        <div class="flex-1 py-2">
          <dp-data-grid
            [columnDefs]="columnDefs()"
            [rowData]="gridRows()"
            [loading]="productsQuery.isPending()"
            [pagination]="false"
            [pageSize]="25"
            height="calc(100vh - 320px)"
            (rowClicked)="selectProduct($event)"
            (gridReady)="onGridReady($event)"
          />

          @if (gridRows().length > 0) {
            <div class="mt-3 flex items-center justify-between text-sm text-[var(--text-secondary)]">
              <span>
                {{ 'analytics.inventory.showing' | translate }}
                {{ gridRows().length }}
                {{ 'analytics.inventory.of' | translate }}
                {{ totalElements() }}
              </span>
              <div class="flex gap-2">
                <button
                  class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1 text-sm transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-40"
                  [disabled]="page() === 0"
                  (click)="prevPage()"
                >
                  {{ 'common.prev' | translate }}
                </button>
                <button
                  class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1 text-sm transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-40"
                  [disabled]="page() >= totalPages() - 1"
                  (click)="nextPage()"
                >
                  {{ 'common.next' | translate }}
                </button>
              </div>
            </div>
          }
        </div>
      </div>

      <!-- Detail panel -->
      @if (selectedProduct(); as product) {
        <div
          class="flex w-[380px] shrink-0 flex-col border-l border-[var(--border-default)] bg-[var(--bg-primary)]"
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
              <p class="mt-0.5 text-sm text-[var(--text-primary)]">{{ product.sourcePlatform }}</p>
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
                  {{ product.daysOfCover }}
                </p>
              </div>
              <div>
                <p class="text-xs text-[var(--text-secondary)]">
                  {{ 'analytics.inventory.col.risk' | translate }}
                </p>
                <p class="mt-1">
                  <span class="inline-flex items-center gap-1.5 text-[length:var(--text-xs)]">
                    <span
                      class="h-1.5 w-1.5 rounded-full"
                      [class]="riskDotClass(product.stockOutRisk)"
                    ></span>
                    {{ riskLabel(product.stockOutRisk) }}
                  </span>
                </p>
              </div>
            </div>
            <div>
              <p class="text-xs text-[var(--text-secondary)]">
                {{ 'analytics.inventory.detail.avg_daily_sales' | translate }}
              </p>
              <p class="mt-0.5 font-mono text-sm text-[var(--text-primary)]">
                {{ product.avgDailySales14d }}
              </p>
            </div>
            <div>
              <p class="text-xs text-[var(--text-secondary)]">
                {{ 'analytics.inventory.detail.cost_price' | translate }}
              </p>
              <p class="mt-0.5 font-mono text-sm text-[var(--text-primary)]">
                {{ formatMoney(product.costPrice) }}
              </p>
            </div>
            <div>
              <p class="text-xs text-[var(--text-secondary)]">
                {{ 'analytics.inventory.col.frozen_capital' | translate }}
              </p>
              <p class="mt-0.5 font-mono text-sm font-semibold text-[var(--text-primary)]">
                {{ formatMoney(product.frozenCapital) }}
              </p>
            </div>
            <div>
              <p class="text-xs text-[var(--text-secondary)]">
                {{ 'analytics.inventory.col.replenishment' | translate }}
              </p>
              <p class="mt-0.5 font-mono text-sm font-semibold text-[var(--text-primary)]">
                {{ product.recommendedReplenishment }}
              </p>
            </div>
          </div>
        </div>
      }
    </div>
  `,
})
export class InventoryByProductPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly t = inject(TranslateService);

  readonly downloadIcon = Download;
  private gridApi: GridApi | null = null;

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.selectedProduct.set(null);
  }

  readonly connectionId = signal(0);
  readonly riskFilter = signal<StockOutRisk | ''>('');
  readonly searchTerm = signal('');
  readonly page = signal(0);
  readonly size = signal(25);
  readonly selectedProduct = signal<InventoryByProduct | null>(null);

  private readonly filter = computed<AnalyticsFilter>(() => {
    const f: AnalyticsFilter = {};
    const cid = this.connectionId();
    if (cid) f.connectionId = cid;
    const risk = this.riskFilter();
    if (risk) f.stockOutRisk = risk;
    const search = this.searchTerm();
    if (search) f.search = search;
    return f;
  });

  readonly productsQuery = injectQuery(() => ({
    queryKey: [
      'analytics', 'inventory-by-product',
      this.wsStore.currentWorkspaceId(),
      this.filter(),
      this.page(),
      this.size(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.listInventoryByProduct(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.page(),
          this.size(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly gridRows = computed(() => this.productsQuery.data()?.content ?? []);
  readonly totalElements = computed(() => this.productsQuery.data()?.totalElements ?? 0);
  readonly totalPages = computed(() => this.productsQuery.data()?.totalPages ?? 0);

  readonly columnDefs = computed<ColDef[]>(() => [
    {
      field: 'skuCode',
      headerName: 'SKU',
      cellClass: 'font-mono text-[11px]',
      cellStyle: () => ({ color: 'var(--text-secondary)' }),
    },
    {
      field: 'productName',
      headerName: this.t.instant('analytics.inventory.col.product'),
      minWidth: 220,
    },
    {
      field: 'sourcePlatform',
      headerName: this.t.instant('analytics.inventory.col.platform'),
      cellRenderer: (p: { value: string }) =>
        `<span class="rounded-full border border-[var(--border-default)] px-2 py-0.5 text-[11px] text-[var(--text-secondary)]">${p.value}</span>`,
    },
    {
      field: 'available',
      headerName: this.t.instant('analytics.inventory.col.available'),
      type: 'rightAligned',
      cellClass: 'font-mono',
    },
    {
      field: 'daysOfCover',
      headerName: this.t.instant('analytics.inventory.col.days_of_cover'),
      type: 'rightAligned',
      cellClass: 'font-mono',
    },
    {
      field: 'stockOutRisk',
      headerName: this.t.instant('analytics.inventory.col.risk'),
      cellRenderer: (p: { value: string }) => {
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
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
    },
    {
      field: 'recommendedReplenishment',
      headerName: this.t.instant('analytics.inventory.col.replenishment'),
      type: 'rightAligned',
      cellClass: 'font-mono',
    },
  ]);

  onGridReady(api: GridApi): void {
    this.gridApi = api;
  }

  exportCsv(): void {
    this.gridApi?.exportDataAsCsv({ fileName: 'inventory-by-product.csv' });
  }

  onConnectionChange(event: Event): void {
    this.connectionId.set(Number((event.target as HTMLSelectElement).value));
    this.page.set(0);
  }

  onRiskFilterChange(event: Event): void {
    this.riskFilter.set((event.target as HTMLSelectElement).value as StockOutRisk | '');
    this.page.set(0);
  }

  onSearchInput(event: Event): void {
    this.searchTerm.set((event.target as HTMLInputElement).value);
    this.page.set(0);
  }

  selectProduct(product: InventoryByProduct): void {
    this.selectedProduct.set(
      this.selectedProduct()?.sellerSkuId === product.sellerSkuId ? null : product,
    );
  }

  prevPage(): void {
    this.page.update((p) => Math.max(0, p - 1));
  }

  nextPage(): void {
    this.page.update((p) => p + 1);
  }

  formatMoney(value: number | null): string {
    return formatMoney(value, 0);
  }

  riskDotClass(risk: string): string {
    switch (risk) {
      case 'CRITICAL': return 'bg-[var(--status-error)]';
      case 'WARNING': return 'bg-[var(--status-warning)]';
      default: return 'bg-[var(--status-success)]';
    }
  }

  riskLabel(risk: string): string {
    return this.t.instant(`analytics.inventory.risk.${risk.toLowerCase()}`);
  }
}
