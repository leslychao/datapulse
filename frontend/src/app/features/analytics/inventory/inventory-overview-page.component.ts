import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import type { EChartsOption } from 'echarts';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { InventoryByProduct } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { StockRiskBadgeComponent } from '@shared/components/stock-risk-badge.component';
import { formatMoney } from '@shared/utils/format.utils';

@Component({
  selector: 'dp-inventory-overview-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, ChartComponent, StockRiskBadgeComponent],
  template: `
    <div class="flex h-full flex-col gap-4">
      <!-- KPI cards -->
      <div class="grid grid-cols-3 gap-3">
        <!-- Total SKUs -->
        <div
          class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4"
        >
          <p class="text-xs text-[var(--text-secondary)]">
            {{ 'analytics.inventory.kpi.total_skus' | translate }}
          </p>
          @if (overviewQuery.isPending()) {
            <div class="dp-shimmer mt-1 h-7 w-20 rounded"></div>
          } @else {
            <p class="mt-1 font-mono text-2xl font-semibold text-[var(--text-primary)]">
              {{ overview()?.totalSkus?.toLocaleString('ru-RU') ?? '—' }}
            </p>
          }
        </div>

        <!-- Critical count -->
        <div
          class="rounded-[var(--radius-lg)] border bg-[var(--bg-primary)] p-4"
          [class]="criticalCount() > 0
            ? 'border-[var(--status-error)]'
            : 'border-[var(--border-default)]'"
        >
          <p class="text-xs text-[var(--text-secondary)]">
            {{ 'analytics.inventory.kpi.critical' | translate }}
          </p>
          @if (overviewQuery.isPending()) {
            <div class="dp-shimmer mt-1 h-7 w-14 rounded"></div>
          } @else {
            <p
              class="mt-1 font-mono text-2xl font-semibold"
              [class]="criticalCount() > 0
                ? 'text-[var(--status-error)]'
                : 'text-[var(--text-primary)]'"
            >
              {{ criticalCount() }}
            </p>
          }
        </div>

        <!-- Frozen Capital -->
        <div
          class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4"
        >
          <p class="text-xs text-[var(--text-secondary)]">
            {{ 'analytics.inventory.kpi.frozen_capital' | translate }}
          </p>
          @if (overviewQuery.isPending()) {
            <div class="dp-shimmer mt-1 h-7 w-28 rounded"></div>
          } @else {
            <p class="mt-1 font-mono text-2xl font-semibold text-[var(--text-primary)]">
              {{ formatMoney(overview()?.frozenCapital ?? null) }}
            </p>
          }
        </div>
      </div>

      <!-- Risk distribution chart -->
      <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
        <p class="mb-2 text-sm font-medium text-[var(--text-primary)]">
          {{ 'analytics.inventory.risk_distribution' | translate }}
        </p>
        <dp-chart
          [options]="riskChartOptions()"
          [loading]="overviewQuery.isPending()"
          height="120px"
        />
      </div>

      <!-- Top-10 critical products -->
      <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
        <p class="mb-3 text-sm font-medium text-[var(--text-primary)]">
          {{ 'analytics.inventory.top_critical' | translate }}
        </p>
        @if (overviewQuery.isPending()) {
          <div class="dp-shimmer h-48 w-full rounded"></div>
        } @else if (topCritical().length === 0) {
          <p class="py-6 text-center text-sm text-[var(--text-tertiary)]">
            {{ 'analytics.inventory.no_critical' | translate }}
          </p>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full text-left text-sm">
              <thead>
                <tr class="border-b border-[var(--border-subtle)] text-xs text-[var(--text-secondary)]">
                  <th class="pb-2 pr-4 font-medium">SKU</th>
                  <th class="pb-2 pr-4 font-medium">
                    {{ 'analytics.inventory.col.product' | translate }}
                  </th>
                  <th class="pb-2 pr-4 text-right font-medium">
                    {{ 'analytics.inventory.col.available' | translate }}
                  </th>
                  <th class="pb-2 pr-4 text-right font-medium">
                    {{ 'analytics.inventory.col.days_of_cover' | translate }}
                  </th>
                  <th class="pb-2 font-medium">
                    {{ 'analytics.inventory.col.risk' | translate }}
                  </th>
                </tr>
              </thead>
              <tbody>
                @for (item of topCritical(); track item.sellerSkuId) {
                  <tr class="border-b border-[var(--border-subtle)] last:border-0">
                    <td class="py-2 pr-4 font-mono text-xs text-[var(--text-secondary)]">
                      {{ item.skuCode }}
                    </td>
                    <td class="max-w-[200px] truncate py-2 pr-4 text-[var(--text-primary)]">
                      {{ item.productName }}
                    </td>
                    <td class="py-2 pr-4 text-right font-mono">
                      {{ item.available }}
                    </td>
                    <td class="py-2 pr-4 text-right font-mono">
                      {{ item.daysOfCover }}
                    </td>
                    <td class="py-2">
                      <dp-stock-risk-badge [risk]="item.stockOutRisk" />
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>
  `,
})
export class InventoryOverviewPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly t = inject(TranslateService);

  readonly overviewQuery = injectQuery(() => ({
    queryKey: ['analytics', 'inventory-overview', this.wsStore.currentWorkspaceId()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.getInventoryOverview(
          this.wsStore.currentWorkspaceId()!,
          {},
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly overview = computed(() => this.overviewQuery.data() ?? null);

  readonly criticalCount = computed(() => this.overview()?.criticalCount ?? 0);

  readonly topCritical = computed<InventoryByProduct[]>(() =>
    this.overview()?.topCritical ?? [],
  );

  readonly riskChartOptions = computed<EChartsOption>(() => {
    const data = this.overview();
    return {
      grid: { left: 80, right: 40, top: 8, bottom: 8 },
      xAxis: { type: 'value', show: false },
      yAxis: {
        type: 'category',
        data: ['NORMAL', 'WARNING', 'CRITICAL'],
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          formatter: (v: string) => this.t.instant(`analytics.inventory.risk.${v.toLowerCase()}`),
          color: 'var(--text-secondary)',
          fontSize: 12,
        },
      },
      series: [
        {
          type: 'bar',
          data: [
            {
              value: data?.normalCount ?? 0,
              itemStyle: { color: 'var(--status-success)', borderRadius: [0, 4, 4, 0] },
            },
            {
              value: data?.warningCount ?? 0,
              itemStyle: { color: 'var(--status-warning)', borderRadius: [0, 4, 4, 0] },
            },
            {
              value: data?.criticalCount ?? 0,
              itemStyle: { color: 'var(--status-error)', borderRadius: [0, 4, 4, 0] },
            },
          ],
          barWidth: 20,
          label: {
            show: true,
            position: 'right',
            color: 'var(--text-secondary)',
            fontSize: 12,
          },
        },
      ],
      tooltip: { show: false },
    };
  });

  formatMoney(value: number | null): string {
    return formatMoney(value, 0);
  }
}
