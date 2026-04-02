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
import { ColDef } from 'ag-grid-community';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { ReturnsByProduct } from '@core/models';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import {
  formatMoney,
  formatPercent,
  currentMonth,
} from '@shared/utils/format.utils';

@Component({
  selector: 'dp-returns-by-product-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, DataGridComponent],
  template: `
    <div class="flex h-full flex-col gap-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3">
        <input
          type="month"
          [value]="period()"
          (change)="onPeriodChange($event)"
          class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]
                 px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-primary)]
                 outline-none focus:border-[var(--accent-primary)]"
        />
        <input
          type="text"
          [value]="search()"
          (input)="onSearchInput($event)"
          [placeholder]="'analytics.returns.search_placeholder' | translate"
          class="w-64 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]
                 px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-primary)]
                 outline-none focus:border-[var(--accent-primary)]"
        />
      </div>

      <div class="flex-1">
        <dp-data-grid
          [columnDefs]="columnDefs()"
          [rowData]="gridRows()"
          [loading]="returnsQuery.isPending()"
          [pagination]="false"
          [pageSize]="50"
          height="calc(100vh - 320px)"
          (rowClicked)="onRowClicked($event)"
        />
      </div>

      @if (returnsQuery.data(); as page) {
        <div class="flex items-center justify-between pb-4 text-[length:var(--text-sm)] text-[var(--text-secondary)]">
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
              {{ selectedProduct()!.skuCode }} · {{ selectedProduct()!.sourcePlatform }}
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
                  <div class="text-[11px] text-[var(--text-tertiary)]">{{ 'analytics.returns.detail.reason' | translate }}</div>
                  <div class="text-sm text-[var(--text-primary)]">
                    {{ selectedProduct()!.topReturnReason }}
                  </div>
                </div>
              </div>
            </section>

            <!-- Financial Impact -->
            <section>
              <h4 class="mb-2 text-xs font-medium uppercase tracking-wide text-[var(--text-tertiary)]">
                {{ 'analytics.returns.detail.financial' | translate }}
              </h4>
              <div class="space-y-2">
                <div class="flex items-center justify-between rounded-[var(--radius-sm)] bg-[var(--bg-secondary)] p-3">
                  <span class="text-sm text-[var(--text-secondary)]">{{ 'analytics.returns.detail.refund_amount' | translate }}</span>
                  <span class="font-mono text-sm text-[var(--status-error)]">
                    {{ formatMoney(selectedProduct()!.financialRefundAmount) }}
                  </span>
                </div>
                <div class="flex items-center justify-between rounded-[var(--radius-sm)] bg-[var(--bg-secondary)] p-3">
                  <span class="text-sm text-[var(--text-secondary)]">{{ 'analytics.returns.detail.penalties_amount' | translate }}</span>
                  <span class="font-mono text-sm text-[var(--status-error)]">
                    {{ formatMoney(selectedProduct()!.penaltiesAmount) }}
                  </span>
                </div>
                <div class="flex items-center justify-between rounded-[var(--radius-sm)] bg-[var(--bg-secondary)] p-3">
                  <span class="text-sm text-[var(--text-secondary)]">{{ 'analytics.returns.detail.sales' | translate }}</span>
                  <span class="font-mono text-sm text-[var(--text-primary)]">
                    {{ selectedProduct()!.saleCount }} / {{ selectedProduct()!.saleQuantity }} {{ 'common.pcs' | translate }}
                  </span>
                </div>
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
  private readonly t = inject(TranslateService);

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.selectedProduct.set(null);
  }

  readonly period = signal(currentMonth());
  readonly search = signal('');
  readonly selectedProduct = signal<ReturnsByProduct | null>(null);
  readonly currentPage = signal(0);
  readonly currentSort = signal('return_rate_pct,desc');
  readonly pageSize = signal(50);

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  readonly returnsQuery = injectQuery(() => ({
    queryKey: [
      'analytics', 'returns-by-product',
      this.wsStore.currentWorkspaceId(),
      this.period(),
      this.search(),
      this.currentPage(),
      this.currentSort(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.listReturnsByProduct(
          this.wsStore.currentWorkspaceId()!,
          { period: this.period(), search: this.search() || undefined },
          this.currentPage(),
          this.pageSize(),
          this.currentSort(),
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
    },
    {
      field: 'productName',
      headerName: this.t.instant('analytics.returns.col.product'),
      minWidth: 200,
    },
    {
      field: 'sourcePlatform',
      headerName: this.t.instant('analytics.returns.col.platform'),
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
      field: 'financialRefundAmount',
      headerName: this.t.instant('analytics.returns.col.refund'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
      cellStyle: () => ({ color: 'var(--status-error)' }),
    },
    {
      field: 'penaltiesAmount',
      headerName: this.t.instant('analytics.returns.col.penalties'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
      cellStyle: () => ({ color: 'var(--status-error)' }),
    },
    {
      field: 'topReturnReason',
      headerName: this.t.instant('analytics.returns.col.reason'),
    },
  ]);

  onPeriodChange(event: Event): void {
    this.period.set((event.target as HTMLInputElement).value);
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

  onRowClicked(row: ReturnsByProduct): void {
    this.selectedProduct.set(row);
  }

  prevPage(): void {
    this.currentPage.update((p) => Math.max(0, p - 1));
  }

  nextPage(): void {
    this.currentPage.update((p) => p + 1);
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
