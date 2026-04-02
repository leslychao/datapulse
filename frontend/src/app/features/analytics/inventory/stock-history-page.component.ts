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
import type { EChartsOption } from 'echarts';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { ConnectionApiService } from '@core/api/connection-api.service';
import { AnalyticsFilter, InventoryByProduct } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { formatMoney } from '@shared/utils/format.utils';

@Component({
  selector: 'dp-stock-history-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, ChartComponent],
  template: `
    <div class="flex h-full flex-col gap-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3">
        <select
          class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
          [value]="connectionId()"
          (change)="onConnectionChange($event)"
        >
          <option [value]="0">{{ 'analytics.filter.all_connections' | translate }}</option>
          @for (conn of connectionsQuery.data() ?? []; track conn.id) {
            <option [value]="conn.id">{{ conn.name }}</option>
          }
        </select>
        <input
          type="number"
          class="w-40 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-sm text-[var(--text-primary)] outline-none placeholder:text-[var(--text-tertiary)] focus:border-[var(--accent-primary)]"
          [placeholder]="'analytics.inventory.filter.product_id' | translate"
          [value]="productId() ?? ''"
          (input)="onProductIdInput($event)"
        />

        <input
          type="date"
          class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
          [value]="dateFrom()"
          (change)="onDateFromChange($event)"
        />

        <span class="text-sm text-[var(--text-tertiary)]">—</span>

        <input
          type="date"
          class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
          [value]="dateTo()"
          (change)="onDateToChange($event)"
        />
      </div>

      <!-- Chart -->
      <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
        <p class="mb-2 text-sm font-medium text-[var(--text-primary)]">
          {{ 'analytics.inventory.stock_history.title' | translate }}
        </p>
        @if (!productId()) {
          <div class="flex h-[360px] items-center justify-center">
            <p class="text-sm text-[var(--text-tertiary)]">
              {{ 'analytics.inventory.stock_history.select_product' | translate }}
            </p>
          </div>
        } @else {
          <dp-chart
            [options]="chartOptions()"
            [loading]="historyQuery.isPending()"
            height="360px"
          />
        }
      </div>

      <!-- Summary strip -->
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
              <span class="inline-flex items-center gap-1.5 text-sm">
                <span
                  class="h-1.5 w-1.5 rounded-full"
                  [class]="riskDotClass(product.stockOutRisk)"
                ></span>
                {{ riskLabel(product.stockOutRisk) }}
              </span>
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
  private readonly connectionApi = inject(ConnectionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly t = inject(TranslateService);

  readonly connectionId = signal(0);
  readonly productId = signal<number | null>(null);
  readonly dateFrom = signal('');
  readonly dateTo = signal('');

  readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections'],
    queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
  }));

  private readonly historyFilter = computed<AnalyticsFilter>(() => {
    const f: AnalyticsFilter = {};
    const cid = this.connectionId();
    if (cid) f.connectionId = cid;
    const pid = this.productId();
    if (pid) f.productId = pid;
    const from = this.dateFrom();
    if (from) f.from = from;
    const to = this.dateTo();
    if (to) f.to = to;
    return f;
  });

  readonly historyQuery = injectQuery(() => ({
    queryKey: ['analytics', 'stock-history', this.wsStore.currentWorkspaceId(), this.historyFilter()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getStockHistory(
          this.wsStore.currentWorkspaceId()!,
          this.historyFilter(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !!this.productId(),
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

  readonly chartOptions = computed<EChartsOption>(() => {
    const points = this.historyQuery.data() ?? [];
    const dates = points.map((p) => p.date);
    const availableData = points.map((p) => p.available);
    const reservedData = points.map((p) => p.reserved);

    return {
      grid: { left: 50, right: 20, top: 20, bottom: 30 },
      xAxis: {
        type: 'category',
        data: dates,
        axisLabel: { color: 'var(--text-secondary)', fontSize: 11 },
        axisLine: { lineStyle: { color: 'var(--border-default)' } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: 'var(--text-secondary)', fontSize: 11 },
        splitLine: { lineStyle: { color: 'var(--border-subtle)' } },
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'var(--bg-primary)',
        borderColor: 'var(--border-default)',
        textStyle: { color: 'var(--text-primary)', fontSize: 12 },
      },
      legend: {
        data: [this.t.instant('analytics.inventory.chart.available'), this.t.instant('analytics.inventory.chart.reserved')],
        top: 0,
        right: 0,
        textStyle: { color: 'var(--text-secondary)', fontSize: 12 },
      },
      series: [
        {
          name: this.t.instant('analytics.inventory.chart.available'),
          type: 'line',
          step: 'end',
          data: availableData,
          lineStyle: { color: 'var(--accent-primary)', width: 2 },
          itemStyle: { color: 'var(--accent-primary)' },
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
          lineStyle: { color: 'var(--status-warning)', width: 2, type: 'dashed' },
          itemStyle: { color: 'var(--status-warning)' },
          symbol: 'none',
        },
      ],
    };
  });

  onConnectionChange(event: Event): void {
    this.connectionId.set(Number((event.target as HTMLSelectElement).value));
  }

  onProductIdInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    this.productId.set(raw ? Number(raw) : null);
  }

  onDateFromChange(event: Event): void {
    this.dateFrom.set((event.target as HTMLInputElement).value);
  }

  onDateToChange(event: Event): void {
    this.dateTo.set((event.target as HTMLInputElement).value);
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
