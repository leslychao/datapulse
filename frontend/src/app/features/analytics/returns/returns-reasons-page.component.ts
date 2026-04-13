import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import type { EChartsOption } from 'echarts';

import { ColDef } from 'ag-grid-community';

import { AnalyticsApiService } from '@core/api/analytics-api.service';
import { MARKETPLACE_REGISTRY } from '@core/models';
import { NavigationStore } from '@shared/stores/navigation.store';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { MonthPickerComponent } from '@shared/components/form/month-picker.component';
import { formatMoney, formatPercent, currentMonth } from '@shared/utils/format.utils';
import {
  UrlFilterDef, isFiltersDefault, resetFilters, initPersistedFilters,
} from '@shared/utils/url-filters';

function monthStart(period: string): string {
  return `${period}-01`;
}

function monthEnd(period: string): string {
  const [y, m] = period.split('-').map(Number);
  const last = new Date(y, m, 0).getDate();
  return `${period}-${String(last).padStart(2, '0')}`;
}

@Component({
  selector: 'dp-returns-reasons-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, ChartComponent, DataGridComponent, MonthPickerComponent],
  template: `
    <div class="flex h-full flex-col gap-4 pb-4">
      <!-- Filter bar -->
      <div class="flex items-center gap-3">
        <dp-month-picker [value]="period()" (valueChange)="period.set($event)" />
        <select
          [value]="platform()"
          (change)="platform.set($any($event.target).value)"
          class="h-8 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]
                 px-2.5 text-[length:var(--text-sm)] text-[var(--text-primary)]
                 outline-none focus:border-[var(--accent-primary)]">
          <option value="">{{ 'analytics.returns.filter.all_platforms' | translate }}</option>
          @for (mp of marketplaces; track mp.type) {
            <option [value]="mp.type">{{ mp.label }}</option>
          }
        </select>
        @if (!filtersDefault()) {
          <button type="button" (click)="onResetFilters()"
            class="h-8 cursor-pointer rounded-[var(--radius-md)] px-3 text-[length:var(--text-sm)]
                   text-[var(--text-tertiary)] transition-colors
                   hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]">
            {{ 'filter_bar.reset_all' | translate }}
          </button>
        }
      </div>

      @if (reasonsQuery.isPending()) {
        <div class="dp-shimmer h-[250px] rounded-[var(--radius-md)]"></div>
        <div class="dp-shimmer h-[300px] rounded-[var(--radius-md)]"></div>
      }

      @if (!reasonsQuery.isPending() && reasons().length === 0) {
        <div class="rounded-[var(--radius-md)] border border-[var(--border-default)]
                    bg-[var(--bg-secondary)] px-4 py-8 text-center">
          <p class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'analytics.returns.reasons.empty' | translate }}
          </p>
        </div>
      }

      @if (reasons().length > 0) {
        <!-- Chart -->
        <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
          <h3 class="mb-3 text-sm font-medium text-[var(--text-primary)]">
            {{ 'analytics.returns.reasons.chart_title' | translate }}
          </h3>
          <dp-chart
            [options]="chartOptions()"
            [loading]="reasonsQuery.isPending()"
            height="250px"
          />
        </div>

        <!-- Table -->
        <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-4">
          <h3 class="mb-3 text-sm font-medium text-[var(--text-primary)]">
            {{ 'analytics.returns.reasons.table_title' | translate }}
          </h3>
          <dp-data-grid
            [columnDefs]="reasonsColumnDefs()"
            [rowData]="reasons()"
            [loading]="reasonsQuery.isPending()"
            [pagination]="false"
            height="400px"
          />
        </div>
      }
    </div>
  `,
})
export class ReturnsReasonsPageComponent {
  private readonly analyticsApi = inject(AnalyticsApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly navStore = inject(NavigationStore);
  private readonly t = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly marketplaces = MARKETPLACE_REGISTRY;

  readonly period = signal(
    this.navStore.getSectionFilterValue<string>('analytics:returns', 'period') ?? currentMonth(),
  );
  readonly platform = signal(
    this.navStore.getSectionFilterValue<string>('analytics:returns', 'platform') ?? '',
  );

  private readonly filterDefs: UrlFilterDef[] = [
    { key: 'period', signal: this.period, defaultValue: currentMonth() },
    { key: 'platform', signal: this.platform, defaultValue: '' },
  ];
  readonly filtersDefault = isFiltersDefault(this.filterDefs);

  constructor() {
    initPersistedFilters(this.router, this.route, {
      pageKey: 'analytics:returns:reasons', filterDefs: this.filterDefs,
    });
    effect(() => {
      const period = this.period();
      const platform = this.platform();
      this.navStore.setSectionFilter('analytics:returns', {
        period, platform,
        from: monthStart(period),
        to: monthEnd(period),
      });
    });
  }

  onResetFilters(): void {
    resetFilters(this.filterDefs);
  }

  readonly reasonsQuery = injectQuery(() => ({
    queryKey: ['analytics', 'returns-reasons', this.wsStore.currentWorkspaceId(), this.period(), this.platform()],
    queryFn: () =>
      lastValueFrom(
        this.analyticsApi.listReturnReasons(
          this.wsStore.currentWorkspaceId()!,
          { period: this.period(), sourcePlatform: this.platform() || undefined },
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly reasons = computed(() => this.reasonsQuery.data() ?? []);

  readonly reasonsColumnDefs = computed<ColDef[]>(() => [
    {
      field: 'reason',
      headerName: this.t.instant('analytics.returns.reasons.col.reason'),
      minWidth: 200,
      flex: 1,
      cellRenderer: (p: any) => {
        if (!p.value) return '<span class="text-[var(--text-tertiary)]">—</span>';
        return p.value;
      },
    },
    {
      field: 'returnCount',
      headerName: this.t.instant('analytics.returns.reasons.col.count'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      width: 100,
      valueFormatter: (p) => p.value != null ? p.value.toLocaleString('ru-RU') : '—',
    },
    {
      field: 'percent',
      headerName: this.t.instant('analytics.returns.reasons.col.percent'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      width: 90,
      valueFormatter: (p) => p.value != null ? formatPercent(p.value) : '—',
    },
    {
      field: 'returnAmount',
      headerName: this.t.instant('analytics.returns.reasons.col.amount'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      width: 120,
      valueFormatter: (p) => formatMoney(p.value, 0),
    },
    {
      field: 'productCount',
      headerName: this.t.instant('analytics.returns.reasons.col.products'),
      type: 'rightAligned',
      cellClass: 'font-mono',
      width: 100,
      valueFormatter: (p) => p.value != null && p.value > 0
          ? p.value.toLocaleString('ru-RU')
          : '—',
    },
    {
      headerName: '',
      width: 90,
      sortable: false,
      cellRenderer: (p: any) => {
        if (!p.data) return '';
        return `<span class="cursor-pointer text-[var(--accent-primary)] text-[11px] font-medium hover:underline"
                      data-action="view">${this.t.instant('analytics.returns.reasons.view_products')}</span>`;
      },
      onCellClicked: (p: any) => {
        if (p.data?.reason) this.navigateToProducts(p.data.reason);
      },
    },
  ]);

  readonly chartOptions = computed<EChartsOption>(() => {
    const items = this.reasons().slice(0, 10);
    return {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: any) => {
          const p = Array.isArray(params) ? params[0] : params;
          const item = items[p.dataIndex];
          if (!item) return '';
          return `<b>${item.reason}</b><br/>`
            + `${this.t.instant('analytics.returns.reasons.col.count')}: ${item.returnCount}<br/>`
            + `${this.t.instant('analytics.returns.reasons.col.percent')}: ${item.percent}%<br/>`
            + `${this.t.instant('analytics.returns.reasons.col.amount')}: ${formatMoney(item.returnAmount, 0)}`;
        },
      },
      grid: { left: 140, right: 60, top: 8, bottom: 8 },
      xAxis: { type: 'value', show: false },
      yAxis: {
        type: 'category',
        data: items.map((i) => i.reason),
        inverse: true,
        axisLabel: {
          width: 130,
          overflow: 'truncate',
          color: 'var(--text-secondary)',
          fontSize: 11,
        },
        axisTick: { show: false },
        axisLine: { show: false },
      },
      series: [{
        type: 'bar',
        data: items.map((i) => i.returnCount),
        barMaxWidth: 20,
        itemStyle: {
          color: 'var(--accent-primary)',
          borderRadius: [0, 4, 4, 0],
        },
        label: {
          show: true,
          position: 'right',
          formatter: (p: any) => {
            const item = items[p.dataIndex];
            return item ? `${item.returnCount} (${item.percent}%)` : '';
          },
          fontSize: 11,
          color: 'var(--text-secondary)',
        },
      }],
    };
  });

  navigateToProducts(reason: string): void {
    this.router.navigate(['../by-product'], {
      relativeTo: this.route,
      queryParams: { search: reason },
    });
  }

}
