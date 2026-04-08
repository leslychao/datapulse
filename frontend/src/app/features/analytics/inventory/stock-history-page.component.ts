import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import type { EChartsOption } from 'echarts';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { DateRangePickerComponent } from '@shared/components/form/date-range-picker.component';
import { StockRiskBadgeComponent } from '@shared/components/stock-risk-badge.component';
import {
  UrlFilterDef, isFiltersDefault, resetFilters, initPersistedFilters, syncFiltersToUrl,
} from '@shared/utils/url-filters';

function resolveCssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || name;
}

function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

function pctDelta(first: number, last: number): number | null {
  if (first === 0) return null;
  return ((last - first) / first) * 100;
}

@Component({
  selector: 'dp-stock-history-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, TranslatePipe, ChartComponent, DateRangePickerComponent, StockRiskBadgeComponent],
  template: `
    <div class="flex h-full flex-col gap-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3">
        <select
          class="w-64 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
          [value]="productIdStr()"
          (change)="onProductSelect($event)"
        >
          <option value="">{{ 'analytics.inventory.stock_history.all_products' | translate }}</option>
          @for (p of productsQuery.data() ?? []; track p.productId) {
            <option [value]="p.productId">{{ p.skuCode }} — {{ p.productName }}</option>
          }
        </select>

        <dp-date-range-picker
          [from]="dateFrom()"
          [to]="dateTo()"
          (fromChange)="dateFrom.set($event)"
          (toChange)="dateTo.set($event)"
        />

        @if (!filtersDefault()) {
          <button type="button" (click)="onResetFilters()"
            class="h-8 cursor-pointer rounded-[var(--radius-md)] px-3 text-[length:var(--text-sm)]
                   text-[var(--text-tertiary)] transition-colors
                   hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]">
            {{ 'filter_bar.reset_all' | translate }}
          </button>
        }
      </div>

      <!-- Chart -->
      <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
        <p class="mb-2 text-sm font-medium text-[var(--text-primary)]">
          @if (productId()) {
            {{ 'analytics.inventory.stock_history.title' | translate }}
          } @else {
            {{ 'analytics.inventory.stock_history.aggregate_title' | translate }}
          }
        </p>
        <dp-chart
          [options]="chartOptions()"
          [loading]="activeQueryPending()"
          height="360px"
        />
      </div>

      <!-- Aggregate KPI strip (no product selected) -->
      @if (!productId()) {
        <div
          class="grid grid-cols-3 gap-3 rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4"
        >
          <div>
            <p class="text-xs text-[var(--text-secondary)]">
              {{ 'analytics.inventory.stock_history.total_available' | translate }}
            </p>
            @if (aggregateQuery.isPending()) {
              <div class="dp-shimmer mt-1 h-7 w-20 rounded"></div>
            } @else {
              <p class="mt-0.5 font-mono text-lg font-semibold text-[var(--text-primary)]">
                {{ aggregateKpi().lastAvailable?.toLocaleString('ru-RU') ?? '—' }}
              </p>
            }
          </div>
          <div>
            <p class="text-xs text-[var(--text-secondary)]">
              {{ 'analytics.inventory.stock_history.total_reserved' | translate }}
            </p>
            @if (aggregateQuery.isPending()) {
              <div class="dp-shimmer mt-1 h-7 w-20 rounded"></div>
            } @else {
              <p class="mt-0.5 font-mono text-lg font-semibold text-[var(--text-primary)]">
                {{ aggregateKpi().lastReserved?.toLocaleString('ru-RU') ?? '—' }}
              </p>
            }
          </div>
          <div>
            <p class="text-xs text-[var(--text-secondary)]">
              {{ 'analytics.inventory.stock_history.change' | translate }}
            </p>
            @if (aggregateQuery.isPending()) {
              <div class="dp-shimmer mt-1 h-7 w-16 rounded"></div>
            } @else {
              @if (aggregateKpi().deltaPct !== null) {
                <p
                  class="mt-0.5 font-mono text-lg font-semibold"
                  [class]="aggregateKpi().deltaPct! >= 0
                    ? 'text-[var(--finance-positive)]'
                    : 'text-[var(--finance-negative)]'"
                >
                  {{ aggregateKpi().deltaPct! >= 0 ? '+' : '' }}{{ aggregateKpi().deltaPct! | number:'1.1-1' }}%
                </p>
              } @else {
                <p class="mt-0.5 font-mono text-lg font-semibold text-[var(--text-primary)]">—</p>
              }
            }
          </div>
        </div>
      }

      <!-- Per-product summary strip -->
      @if (productId() && inventoryQuery.data(); as product) {
        <div
          class="grid grid-cols-4 gap-3 rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4"
        >
          <div>
            <p class="text-xs text-[var(--text-secondary)]">
              {{ 'analytics.inventory.stock_history.current_available' | translate }}
            </p>
            <p class="mt-0.5 font-mono text-lg font-semibold text-[var(--text-primary)]">
              {{ product.available }}
            </p>
          </div>
          <div>
            <p class="text-xs text-[var(--text-secondary)]">
              {{ 'analytics.inventory.col.risk' | translate }}
            </p>
            <p class="mt-1">
              <dp-stock-risk-badge [risk]="product.stockOutRisk" />
            </p>
          </div>
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
              {{ 'analytics.inventory.col.replenishment' | translate }}
            </p>
            <p class="mt-0.5 font-mono text-lg font-semibold text-[var(--text-primary)]">
              {{ product.recommendedReplenishment }}
            </p>
          </div>
        </div>
      }
    </div>
  `,
})
export class StockHistoryPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly t = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly productId = signal<number | null>(null);
  readonly productIdStr = computed(() => this.productId()?.toString() ?? '');
  readonly dateFrom = signal(daysAgo(30));
  readonly dateTo = signal(daysAgo(0));

  private readonly filterDefs: UrlFilterDef[] = [
    { key: 'from', signal: this.dateFrom, defaultValue: daysAgo(30) },
    { key: 'to', signal: this.dateTo, defaultValue: daysAgo(0) },
  ];
  readonly filtersDefault = isFiltersDefault(this.filterDefs);

  constructor() {
    initPersistedFilters(this.router, this.route, {
      pageKey: 'analytics:inventory:stock-history', filterDefs: this.filterDefs,
    });
    const qpProductId = this.route.snapshot.queryParams['productId'];
    if (qpProductId) this.productId.set(Number(qpProductId));
    syncFiltersToUrl(this.router, this.route, [
      ...this.filterDefs,
      { key: 'productId', signal: this.productIdStr as any, defaultValue: '' },
    ]);
  }

  onResetFilters(): void {
    resetFilters(this.filterDefs);
    this.productId.set(null);
  }

  readonly productsQuery = injectQuery(() => ({
    queryKey: ['analytics', 'stock-history-products', this.wsStore.currentWorkspaceId()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.listInventoryByProduct(
          this.wsStore.currentWorkspaceId()!,
          {},
          0,
          200,
        ),
      ).then((page) => page.content),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly aggregateQuery = injectQuery(() => ({
    queryKey: [
      'analytics', 'stock-history-aggregate',
      this.wsStore.currentWorkspaceId(),
      this.dateFrom(),
      this.dateTo(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getStockHistory(
          this.wsStore.currentWorkspaceId()!,
          this.dateFrom(),
          this.dateTo(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId()
      && !this.productId()
      && !!this.dateFrom()
      && !!this.dateTo(),
    staleTime: 60_000,
  }));

  readonly historyQuery = injectQuery(() => ({
    queryKey: [
      'analytics', 'stock-history',
      this.wsStore.currentWorkspaceId(),
      this.productId(),
      this.dateFrom(),
      this.dateTo(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getStockHistory(
          this.wsStore.currentWorkspaceId()!,
          this.dateFrom(),
          this.dateTo(),
          this.productId(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId()
      && !!this.productId()
      && !!this.dateFrom()
      && !!this.dateTo(),
    staleTime: 60_000,
  }));

  readonly inventoryQuery = injectQuery(() => ({
    queryKey: ['analytics', 'inventory-product-summary', this.wsStore.currentWorkspaceId(), this.productId()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.listInventoryByProduct(
          this.wsStore.currentWorkspaceId()!,
          { productId: this.productId()! },
          0,
          1,
        ),
      ).then((page) => page.content[0] ?? null),
    enabled: !!this.wsStore.currentWorkspaceId() && !!this.productId(),
    staleTime: 60_000,
  }));

  readonly activeQueryPending = computed(() =>
    this.productId() ? this.historyQuery.isPending() : this.aggregateQuery.isPending(),
  );

  readonly aggregateKpi = computed(() => {
    const points = this.aggregateQuery.data() ?? [];
    if (points.length === 0) {
      return { lastAvailable: null, lastReserved: null, deltaPct: null };
    }
    const first = points[0];
    const last = points[points.length - 1];
    return {
      lastAvailable: last.available,
      lastReserved: last.reserved ?? 0,
      deltaPct: pctDelta(first.available, last.available),
    };
  });

  readonly chartOptions = computed<EChartsOption>(() => {
    const points = this.productId()
      ? (this.historyQuery.data() ?? [])
      : (this.aggregateQuery.data() ?? []);
    const dates = points.map((p) => p.date);
    const availableData = points.map((p) => p.available);
    const reservedData = points.map((p) => p.reserved ?? 0);

    const accentColor = resolveCssVar('--accent-primary');
    const warningColor = resolveCssVar('--status-warning');

    return {
      grid: { left: 50, right: 20, top: 20, bottom: 30 },
      xAxis: {
        type: 'category',
        data: dates,
        axisLabel: { color: resolveCssVar('--text-secondary'), fontSize: 11 },
        axisLine: { lineStyle: { color: resolveCssVar('--border-default') } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: resolveCssVar('--text-secondary'), fontSize: 11 },
        splitLine: { lineStyle: { color: resolveCssVar('--border-subtle') } },
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: resolveCssVar('--bg-primary'),
        borderColor: resolveCssVar('--border-default'),
        textStyle: { color: resolveCssVar('--text-primary'), fontSize: 12 },
      },
      legend: {
        data: [
          this.t.instant('analytics.inventory.chart.available'),
          this.t.instant('analytics.inventory.chart.reserved'),
        ],
        top: 0,
        right: 0,
        textStyle: { color: resolveCssVar('--text-secondary'), fontSize: 12 },
      },
      series: [
        {
          name: this.t.instant('analytics.inventory.chart.available'),
          type: 'line',
          step: 'end',
          data: availableData,
          lineStyle: { color: accentColor, width: 2 },
          itemStyle: { color: accentColor },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(59, 130, 246, 0.15)' },
                { offset: 1, color: 'rgba(59, 130, 246, 0.02)' },
              ],
            } as Record<string, unknown>,
          },
          symbol: 'none',
        },
        {
          name: this.t.instant('analytics.inventory.chart.reserved'),
          type: 'line',
          step: 'end',
          data: reservedData,
          lineStyle: { color: warningColor, width: 2, type: 'dashed' },
          itemStyle: { color: warningColor },
          symbol: 'none',
        },
      ],
    };
  });

  onProductSelect(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.productId.set(value ? Number(value) : null);
  }
}
