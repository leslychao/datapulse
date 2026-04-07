import {
  ChangeDetectionStrategy,
  Component,
  computed,
  HostListener,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { ColDef, GridApi } from 'ag-grid-community';
import { LucideAngularModule, Download } from 'lucide-angular';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { PnlByPosting } from '@core/models';
import { MonthPickerComponent } from '@shared/components/form/month-picker.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import {
  formatMoney,
  financeColor,
  formatDateTime,
  currentMonth,
} from '@shared/utils/format.utils';

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
  imports: [TranslatePipe, MonthPickerComponent, DataGridComponent, LucideAngularModule],
  template: `
    <div class="flex h-full">
      <div class="flex flex-1 flex-col overflow-hidden gap-4">
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
          [clickableRows]="true"
          (rowClicked)="onRowClicked($event)"
          (cellDoubleClicked)="onRowDoubleClicked($event)"
          (gridReady)="onGridReady($event)"
        />

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

      @if (selectedPosting(); as posting) {
        <div class="flex w-[380px] shrink-0 flex-col border-l border-[var(--border-default)] bg-[var(--bg-primary)]">
          <div class="flex items-center justify-between border-b border-[var(--border-default)] px-4 py-3">
            <h3 class="text-[length:var(--text-sm)] font-semibold text-[var(--text-primary)]">
              {{ 'analytics.pnl.posting_detail.title' | translate }}
            </h3>
            <button
              class="cursor-pointer rounded p-1 text-[var(--text-tertiary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
              aria-label="Close"
              (click)="selectedPosting.set(null)"
            >
              ✕
            </button>
          </div>

          <div class="flex-1 space-y-4 overflow-auto p-4">
            <div>
              <p class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                {{ 'analytics.pnl.col.posting_id' | translate }}
              </p>
              <p class="mt-0.5 font-mono text-[length:var(--text-sm)] text-[var(--accent-primary)]">
                {{ posting.postingId }}
              </p>
            </div>
            <div>
              <p class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                {{ 'analytics.pnl.col.sku' | translate }}
              </p>
              <p class="mt-0.5 font-mono text-[length:var(--text-sm)] text-[var(--text-primary)]">
                {{ posting.skuCode }}
              </p>
            </div>
            <div>
              <p class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                {{ 'analytics.pnl.col.product' | translate }}
              </p>
              <p class="mt-0.5 text-[length:var(--text-sm)] text-[var(--text-primary)]">
                {{ posting.productName }}
              </p>
            </div>
            <div>
              <p class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                {{ 'analytics.pnl.col.date' | translate }}
              </p>
              <p class="mt-0.5 text-[length:var(--text-sm)] text-[var(--text-primary)]">
                {{ formatDate(posting.financeDate) }}
              </p>
            </div>

            <div class="border-t border-[var(--border-subtle)] pt-4">
              <div class="grid grid-cols-2 gap-4">
                <div>
                  <p class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                    {{ 'analytics.pnl.col.revenue' | translate }}
                  </p>
                  <p class="mt-0.5 font-mono text-[length:var(--text-sm)] font-semibold"
                     [style.color]="financeColor(posting.revenueAmount)">
                    {{ formatMoney(posting.revenueAmount) }}
                  </p>
                </div>
                <div>
                  <p class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                    {{ 'analytics.pnl.col.commission' | translate }}
                  </p>
                  <p class="mt-0.5 font-mono text-[length:var(--text-sm)] font-semibold text-[var(--finance-negative)]">
                    {{ formatMoney(posting.marketplaceCommissionAmount) }}
                  </p>
                </div>
                <div>
                  <p class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                    {{ 'analytics.pnl.col.logistics' | translate }}
                  </p>
                  <p class="mt-0.5 font-mono text-[length:var(--text-sm)] font-semibold text-[var(--finance-negative)]">
                    {{ formatMoney(posting.logisticsCostAmount) }}
                  </p>
                </div>
                <div>
                  <p class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                    {{ 'analytics.pnl.col.payout' | translate }}
                  </p>
                  <p class="mt-0.5 font-mono text-[length:var(--text-sm)] font-semibold"
                     [style.color]="financeColor(posting.netPayout)">
                    {{ formatMoney(posting.netPayout) }}
                  </p>
                </div>
                <div>
                  <p class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                    {{ 'analytics.pnl.col.cogs' | translate }}
                  </p>
                  <p class="mt-0.5 font-mono text-[length:var(--text-sm)] font-semibold"
                     [style.color]="financeColor(posting.netCogs)">
                    {{ formatMoney(posting.netCogs) }}
                  </p>
                </div>
                <div>
                  <p class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                    {{ 'analytics.pnl.col.residual' | translate }}
                  </p>
                  <p class="mt-0.5 font-mono text-[length:var(--text-sm)] font-semibold"
                     [class]="residualColorClass(posting.reconciliationResidual)">
                    {{ formatMoney(posting.reconciliationResidual) }}
                  </p>
                </div>
              </div>
            </div>

            <div class="border-t border-[var(--border-subtle)] pt-4">
              <button
                (click)="openPosting(posting.postingId)"
                class="w-full rounded-[var(--radius-md)] border border-[var(--accent-primary)] px-3 py-1.5
                       text-[length:var(--text-sm)] font-medium text-[var(--accent-primary)]
                       transition-colors hover:bg-[var(--accent-subtle)]"
              >
                {{ 'analytics.pnl.posting_detail.view_full' | translate }}
              </button>
            </div>
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
  private readonly t = inject(TranslateService);

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.selectedPosting.set(null);
  }

  readonly downloadIcon = Download;
  readonly period = signal(currentMonth());
  readonly search = signal('');
  readonly currentPage = signal(0);
  readonly pageSize = signal(50);
  readonly selectedPosting = signal<PnlByPosting | null>(null);

  private gridApi: GridApi | null = null;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

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
      cellStyle: () => ({ color: 'var(--accent-primary)', cursor: 'pointer' }),
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
      field: 'netPayout',
      headerName: this.t.instant('analytics.pnl.col.payout'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
    },
    {
      field: 'netCogs',
      headerName: this.t.instant('analytics.pnl.col.cogs'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
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

  onSearchInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.search.set(input.value);
      this.currentPage.set(0);
    }, 300);
  }

  onRowClicked(row: PnlByPosting): void {
    this.selectedPosting.set(
      this.selectedPosting()?.postingId === row.postingId ? null : row,
    );
  }

  onRowDoubleClicked(row: PnlByPosting): void {
    this.openPosting(row.postingId);
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
    return formatDateTime(iso);
  }

  financeColor(value: number | null): string {
    return financeColor(value);
  }

  residualColorClass(value: number): string {
    if (value !== 0) return 'text-[var(--status-warning)]';
    return 'text-[var(--finance-zero)]';
  }
}
