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
import { ColDef, GridApi, ValueFormatterParams } from 'ag-grid-community';
import { LucideAngularModule, Download } from 'lucide-angular';

import { AdvertisingApiService } from '@core/api/advertising-api.service';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import {
  formatMoney,
  formatPercent,
  renderBadge,
  renderOutlineBadge,
} from '@shared/utils/format.utils';

type Period = '7d' | '30d';

const STATUS_COLORS: Record<string, string> = {
  active: 'var(--status-success)',
  on_pause: 'var(--status-warning)',
  paused: 'var(--status-warning)',
  archived: 'var(--status-neutral)',
  ready: 'var(--status-info)',
};

const TREND_SYMBOL: Record<string, string> = {
  UP: '↑',
  DOWN: '↓',
  FLAT: '→',
};

const TREND_COLOR: Record<string, string> = {
  UP: 'var(--finance-negative)',
  DOWN: 'var(--finance-positive)',
  FLAT: 'var(--text-secondary)',
};

@Component({
  selector: 'dp-campaigns-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, DataGridComponent, LucideAngularModule],
  template: `
    <div class="flex flex-col gap-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3">
        <h2 class="text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]">
          {{ 'advertising.campaigns.title' | translate }}
        </h2>

        <div class="ml-auto flex items-center gap-2">
          <!-- Period selector -->
          <div class="flex rounded-[var(--radius-md)] border border-[var(--border-default)]">
            @for (p of periods; track p.value) {
              <button
                (click)="onPeriodChange(p.value)"
                [class]="period() === p.value
                  ? 'bg-[var(--accent-subtle)] text-[var(--accent-primary)] font-medium'
                  : 'text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]'"
                class="px-3 py-1 text-[length:var(--text-sm)] transition-colors first:rounded-l-[var(--radius-md)] last:rounded-r-[var(--radius-md)]"
              >
                {{ p.labelKey | translate }}
              </button>
            }
          </div>

          <!-- Status filter -->
          <select
            (change)="onStatusChange($event)"
            class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]
                   px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-primary)]
                   outline-none focus:border-[var(--accent-primary)]"
          >
            <option value="">{{ 'advertising.campaigns.filter.all_statuses' | translate }}</option>
            <option value="active">{{ 'advertising.campaigns.status.active' | translate }}</option>
            <option value="on_pause">{{ 'advertising.campaigns.status.paused' | translate }}</option>
            <option value="archived">{{ 'advertising.campaigns.status.archived' | translate }}</option>
          </select>

          <!-- Export -->
          <button
            (click)="exportCsv()"
            class="flex items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--border-default)]
                   bg-[var(--bg-primary)] px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-secondary)]
                   transition-colors hover:bg-[var(--bg-tertiary)]"
          >
            <lucide-icon [img]="downloadIcon" size="14" />
            <span>{{ 'common.export_csv' | translate }}</span>
          </button>
        </div>
      </div>

      <!-- Grid -->
      <dp-data-grid
        [columnDefs]="columnDefs()"
        [rowData]="gridRows()"
        [loading]="campaignsQuery.isPending()"
        [pagination]="false"
        [pageSize]="50"
        height="calc(100vh - 280px)"
        (gridReady)="onGridReady($event)"
      />

      <!-- Pagination -->
      @if (campaignsQuery.data(); as page) {
        @if (page.totalElements > 0) {
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
      }
    </div>
  `,
})
export class CampaignsPageComponent {
  private readonly advertisingApi = inject(AdvertisingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly t = inject(TranslateService);

  readonly downloadIcon = Download;
  readonly period = signal<Period>('30d');
  readonly statusFilter = signal<string>('');
  readonly currentPage = signal(0);
  readonly pageSize = signal(50);

  private gridApi: GridApi | null = null;

  readonly periods = [
    { value: '7d' as Period, labelKey: 'advertising.campaigns.period.7d' },
    { value: '30d' as Period, labelKey: 'advertising.campaigns.period.30d' },
  ];

  readonly campaignsQuery = injectQuery(() => ({
    queryKey: [
      'advertising', 'campaigns',
      this.wsStore.currentWorkspaceId(),
      this.period(),
      this.statusFilter(),
      this.currentPage(),
      this.pageSize(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.advertisingApi.listCampaigns(
          this.wsStore.currentWorkspaceId()!,
          { period: this.period(), status: this.statusFilter() || undefined },
          this.currentPage(),
          this.pageSize(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly gridRows = computed(() => this.campaignsQuery.data()?.content ?? []);

  readonly columnDefs = computed<ColDef[]>(() => [
    {
      field: 'name',
      headerName: this.t.instant('advertising.campaigns.col.name'),
      tooltipField: 'name',
      minWidth: 220,
      flex: 1,
    },
    {
      field: 'sourcePlatform',
      headerName: this.t.instant('advertising.campaigns.col.platform'),
      headerTooltip: this.t.instant('advertising.campaigns.col.platform'),
      width: 90,
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
      field: 'campaignType',
      headerName: this.t.instant('advertising.campaigns.col.type'),
      headerTooltip: this.t.instant('advertising.campaigns.col.type'),
      width: 120,
      valueFormatter: (params: ValueFormatterParams) =>
        params.value
          ? this.t.instant('advertising.campaign_type.' + params.value)
          : '—',
      cellRenderer: (p: { value: string }) => {
        if (!p.value) return '—';
        const key = 'advertising.campaign_type.' + p.value;
        const label = this.t.instant(key);
        return renderOutlineBadge(label !== key ? label : p.value);
      },
    },
    {
      field: 'status',
      headerName: this.t.instant('advertising.campaigns.col.status'),
      headerTooltip: this.t.instant('advertising.campaigns.col.status'),
      width: 120,
      cellRenderer: (p: { value: string }) => {
        const color = STATUS_COLORS[p.value] ?? 'var(--status-neutral)';
        const key = `advertising.campaigns.status.${p.value}`;
        const label = this.t.instant(key);
        return renderBadge(label !== key ? label : p.value, color);
      },
    },
    {
      field: 'dailyBudget',
      headerName: this.t.instant('advertising.campaigns.col.daily_budget'),
      type: 'rightAligned',
      width: 130,
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
    },
    {
      field: 'spendForPeriod',
      headerName: this.t.instant('advertising.campaigns.col.spend'),
      type: 'rightAligned',
      width: 140,
      cellClass: 'font-mono',
      valueFormatter: (p) => formatMoney(p.value, 0),
    },
    {
      field: 'ordersForPeriod',
      headerName: this.t.instant('advertising.campaigns.col.orders'),
      headerTooltip: this.t.instant('advertising.campaigns.col.orders'),
      type: 'rightAligned',
      width: 100,
      cellClass: 'font-mono',
      valueFormatter: (p) =>
        p.value !== null && p.value !== undefined ? String(p.value) : '—',
    },
    {
      field: 'drrPct',
      headerName: this.t.instant('advertising.campaigns.col.drr'),
      headerTooltip: this.t.instant('advertising.campaigns.col.drr'),
      type: 'rightAligned',
      width: 100,
      cellClass: 'font-mono',
      valueFormatter: (p) => formatPercent(p.value),
    },
    {
      field: 'drrTrend',
      headerName: this.t.instant('advertising.campaigns.col.drr_trend'),
      headerTooltip: this.t.instant('advertising.campaigns.col.drr_trend'),
      width: 80,
      cellRenderer: (p: { value: string | null }) => {
        if (!p.value) return '—';
        const sym = TREND_SYMBOL[p.value] ?? '→';
        const color = TREND_COLOR[p.value] ?? 'var(--text-secondary)';
        return `<span style="color:${color};font-weight:600">${sym}</span>`;
      },
    },
  ]);

  onGridReady(api: GridApi): void {
    this.gridApi = api;
  }

  exportCsv(): void {
    this.gridApi?.exportDataAsCsv({ fileName: 'advertising-campaigns.csv' });
  }

  onPeriodChange(value: Period): void {
    this.period.set(value);
    this.currentPage.set(0);
  }

  onStatusChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.statusFilter.set(select.value);
    this.currentPage.set(0);
  }

  prevPage(): void {
    this.currentPage.update((p) => Math.max(0, p - 1));
  }

  nextPage(): void {
    this.currentPage.update((p) => p + 1);
  }
}
