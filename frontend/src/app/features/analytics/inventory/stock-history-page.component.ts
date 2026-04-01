import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import type { EChartsOption } from 'echarts';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { AnalyticsFilter, InventoryByProduct } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';

@Component({
  selector: 'dp-stock-history-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, ChartComponent],
  template: `
    <div class="flex h-full flex-col gap-4 p-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3">
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
                  class="h-2.5 w-2.5 rounded-full"
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
  private readonly wsStore = inject(WorkspaceContextStore);

  readonly productId = signal<number | null>(null);
  readonly dateFrom = signal('');
  readonly dateTo = signal('');

  private readonly historyFilter = computed<AnalyticsFilter>(() => {
    const f: AnalyticsFilter = {};
    const pid = this.productId();
    if (pid) f.productId = pid;
    const from = this.dateFrom();
    if (from) f.from = from;
    const to = this.dateTo();
    if (to) f.to = to;
    return f;
  });

  readonly historyQuery = injectQuery(() => ({
    queryKey: ['stock-history', this.wsStore.currentWorkspaceId(), this.historyFilter()],
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
    queryKey: ['inventory-product-summary', this.wsStore.currentWorkspaceId(), this.productId()],
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
        data: ['Доступно', 'В резерве'],
        top: 0,
        right: 0,
        textStyle: { color: 'var(--text-secondary)', fontSize: 12 },
      },
      series: [
        {
          name: 'Доступно',
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
            } as any,
          },
          symbol: 'none',
        },
        {
          name: 'В резерве',
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
    if (value == null) return '—';
    const abs = Math.abs(value);
    const formatted = abs.toLocaleString('ru-RU', { maximumFractionDigits: 0 });
    return value < 0 ? `−${formatted} ₽` : `${formatted} ₽`;
  }

  riskDotClass(risk: string): string {
    switch (risk) {
      case 'CRITICAL': return 'bg-[var(--status-error)]';
      case 'WARNING': return 'bg-[var(--status-warning)]';
      default: return 'bg-[var(--status-success)]';
    }
  }

  riskLabel(risk: string): string {
    switch (risk) {
      case 'CRITICAL': return 'Критичный';
      case 'WARNING': return 'Внимание';
      default: return 'Норма';
    }
  }
}
