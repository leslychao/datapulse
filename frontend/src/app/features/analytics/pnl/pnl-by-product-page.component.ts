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

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { PnlByProduct } from '@core/models';
import { MonthPickerComponent } from '@shared/components/form/month-picker.component';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { formatMoney, currentMonth } from '@shared/utils/format.utils';

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
  imports: [TranslatePipe, MonthPickerComponent],
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
      </div>

      <!-- Table -->
      <div class="overflow-x-auto rounded-[var(--radius-md)] border border-[var(--border-default)]">
        <table class="w-full text-left text-[length:var(--text-sm)]">
          <thead class="bg-[var(--bg-secondary)] text-[length:var(--text-xs)] text-[var(--text-secondary)]">
            <tr>
              <th class="px-3 py-2 font-medium">{{ 'analytics.pnl.col.sku' | translate }}</th>
              <th class="px-3 py-2 font-medium">{{ 'analytics.pnl.col.product' | translate }}</th>
              <th class="px-3 py-2 font-medium">{{ 'analytics.pnl.col.platform' | translate }}</th>
              <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.revenue' | translate }}</th>
              <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.commission' | translate }}</th>
              <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.logistics' | translate }}</th>
              <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.refunds' | translate }}</th>
              <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.cogs' | translate }}</th>
              <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.full_pnl' | translate }}</th>
              <th class="px-3 py-2 font-medium">{{ 'analytics.pnl.col.cogs_status' | translate }}</th>
            </tr>
          </thead>
          <tbody>
            @if (productsQuery.isPending()) {
              @for (i of shimmerRows; track i) {
                <tr>
                  <td colspan="10" class="px-3 py-2">
                    <div class="dp-shimmer h-4 rounded"></div>
                  </td>
                </tr>
              }
            }
            @if (productsQuery.data(); as page) {
              @for (row of page.content; track row.sellerSkuId) {
                <tr class="border-t border-[var(--border-subtle)] transition-colors hover:bg-[var(--bg-tertiary)]">
                  <td class="px-3 py-2 font-mono text-[length:var(--text-xs)]">{{ row.skuCode }}</td>
                  <td class="max-w-[200px] truncate px-3 py-2">{{ row.productName }}</td>
                  <td class="px-3 py-2">
                    <span class="rounded-[var(--radius-sm)] px-1.5 py-0.5 text-[length:var(--text-xs)] font-medium"
                          [class]="platformBadge(row.sourcePlatform)">
                      {{ row.sourcePlatform }}
                    </span>
                  </td>
                  <td class="px-3 py-2 text-right font-mono">{{ formatMoney(row.revenueAmount) }}</td>
                  <td class="px-3 py-2 text-right font-mono text-[var(--finance-negative)]">
                    {{ formatMoney(row.marketplaceCommissionAmount) }}
                  </td>
                  <td class="px-3 py-2 text-right font-mono text-[var(--finance-negative)]">
                    {{ formatMoney(row.logisticsCostAmount) }}
                  </td>
                  <td class="px-3 py-2 text-right font-mono text-[var(--finance-negative)]">
                    {{ formatMoney(row.refundAmount) }}
                  </td>
                  <td class="px-3 py-2 text-right font-mono">{{ formatMoney(row.netCogs) }}</td>
                  <td class="px-3 py-2 text-right font-mono font-semibold" [class]="moneyColorClass(row.fullPnl)">
                    {{ formatMoney(row.fullPnl) }}
                  </td>
                  <td class="px-3 py-2">
                    <span class="rounded-[var(--radius-sm)] px-1.5 py-0.5 text-[length:var(--text-xs)] font-medium"
                          [class]="cogsStatusColor(row.cogsStatus)">
                      {{ cogsStatusLabel(row.cogsStatus) }}
                    </span>
                  </td>
                </tr>
              }
              @if (page.content.length === 0) {
                <tr>
                  <td colspan="10" class="px-3 py-8 text-center text-[var(--text-secondary)]">
                    {{ 'analytics.pnl.empty' | translate }}
                  </td>
                </tr>
              }
            }
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
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

  readonly period = signal(currentMonth());
  readonly search = signal('');
  readonly currentPage = signal(0);
  readonly pageSize = signal(50);

  readonly shimmerRows = Array.from({ length: 8 });

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

  prevPage(): void {
    this.currentPage.update((p) => Math.max(0, p - 1));
  }

  nextPage(): void {
    this.currentPage.update((p) => p + 1);
  }

  formatMoney(value: number | null): string {
    return formatMoney(value, 0);
  }

  moneyColorClass(value: number | null): string {
    if (value != null && value > 0) return 'text-[var(--finance-positive)]';
    if (value != null && value < 0) return 'text-[var(--finance-negative)]';
    return 'text-[var(--finance-zero)]';
  }

  platformBadge(platform: string): string {
    if (platform === 'WB') return 'bg-[var(--mp-wb-bg)] text-[var(--mp-wb)]';
    if (platform === 'OZON') return 'bg-[var(--mp-ozon-bg)] text-[var(--mp-ozon)]';
    return 'bg-[var(--status-neutral-bg)] text-[var(--status-neutral)]';
  }

  cogsStatusLabel(status: string): string {
    const key = COGS_STATUS_KEY[status];
    return key ? this.t.instant(key) : status;
  }

  cogsStatusColor(status: string): string {
    return COGS_STATUS_COLOR[status] ?? COGS_STATUS_COLOR['NO_SALES'];
  }
}
