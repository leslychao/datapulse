import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { MonthPickerComponent } from '@shared/components/form/month-picker.component';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { formatMoney } from '@shared/utils/format.utils';

function currentMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

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
          [placeholder]="'analytics.pnl.search_sku' | translate"
          class="w-52 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]
                 px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-primary)]
                 outline-none focus:border-[var(--accent-primary)]"
        />
      </div>

      <!-- Table -->
      <div class="overflow-x-auto rounded-[var(--radius-md)] border border-[var(--border-default)]">
        <table class="w-full text-left text-[length:var(--text-sm)]">
          <thead class="bg-[var(--bg-secondary)] text-[length:var(--text-xs)] text-[var(--text-secondary)]">
            <tr>
              <th class="px-3 py-2 font-medium">{{ 'analytics.pnl.col.posting_id' | translate }}</th>
              <th class="px-3 py-2 font-medium">{{ 'analytics.pnl.col.sku' | translate }}</th>
              <th class="px-3 py-2 font-medium">{{ 'analytics.pnl.col.product' | translate }}</th>
              <th class="px-3 py-2 font-medium">{{ 'analytics.pnl.col.date' | translate }}</th>
              <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.revenue' | translate }}</th>
              <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.commission' | translate }}</th>
              <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.logistics' | translate }}</th>
              <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.payout' | translate }}</th>
              <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.cogs' | translate }}</th>
              <th class="px-3 py-2 font-medium text-right">{{ 'analytics.pnl.col.residual' | translate }}</th>
            </tr>
          </thead>
          <tbody>
            @if (postingsQuery.isPending()) {
              @for (i of shimmerRows; track i) {
                <tr>
                  <td colspan="10" class="px-3 py-2">
                    <div class="dp-shimmer h-4 rounded"></div>
                  </td>
                </tr>
              }
            }
            @if (postingsQuery.data(); as page) {
              @for (row of page.content; track row.postingId) {
                <tr
                  class="cursor-pointer border-t border-[var(--border-subtle)] transition-colors
                         hover:bg-[var(--bg-tertiary)]"
                  (click)="openPosting(row.postingId)"
                >
                  <td class="px-3 py-2 font-mono text-[length:var(--text-xs)] text-[var(--accent-primary)]">
                    {{ row.postingId }}
                  </td>
                  <td class="px-3 py-2 font-mono text-[length:var(--text-xs)]">{{ row.skuCode }}</td>
                  <td class="max-w-[180px] truncate px-3 py-2">{{ row.productName }}</td>
                  <td class="px-3 py-2 text-[var(--text-secondary)]">{{ formatDate(row.financeDate) }}</td>
                  <td class="px-3 py-2 text-right font-mono">{{ formatMoney(row.revenueAmount) }}</td>
                  <td class="px-3 py-2 text-right font-mono text-[var(--finance-negative)]">
                    {{ formatMoney(row.marketplaceCommissionAmount) }}
                  </td>
                  <td class="px-3 py-2 text-right font-mono text-[var(--finance-negative)]">
                    {{ formatMoney(row.logisticsCostAmount) }}
                  </td>
                  <td class="px-3 py-2 text-right font-mono">{{ formatMoney(row.netPayout) }}</td>
                  <td class="px-3 py-2 text-right font-mono">{{ formatMoney(row.netCogs) }}</td>
                  <td
                    class="px-3 py-2 text-right font-mono font-semibold"
                    [class]="residualColorClass(row.reconciliationResidual)"
                  >
                    {{ formatMoney(row.reconciliationResidual) }}
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
      @if (postingsQuery.data(); as page) {
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
export class PnlByPostingPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);

  readonly period = signal(currentMonth());
  readonly search = signal('');
  readonly currentPage = signal(0);
  readonly pageSize = signal(20);

  readonly shimmerRows = Array.from({ length: 8 });

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  readonly postingsQuery = injectQuery(() => ({
    queryKey: [
      'pnl-by-posting',
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
    }, 400);
  }

  openPosting(postingId: string): void {
    this.router.navigate(['/analytics/pnl/posting', postingId]);
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

  formatDate(iso: string): string {
    if (!iso) return '—';
    const [y, m, d] = iso.split('-');
    return `${d}.${m}.${y}`;
  }

  residualColorClass(value: number): string {
    if (value !== 0) return 'text-[var(--status-warning)]';
    return 'text-[var(--finance-zero)]';
  }
}
