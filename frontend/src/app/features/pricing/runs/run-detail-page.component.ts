import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PricingApiService } from '@core/api/pricing-api.service';
import { formatMoney, formatDateTime } from '@shared/utils/format.utils';
import { PricingDecisionFilter, PricingDecisionSummary } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';

const RUN_STATUS_COLOR: Record<string, string> = {
  PENDING: 'info',
  IN_PROGRESS: 'info',
  COMPLETED: 'success',
  COMPLETED_WITH_ERRORS: 'warning',
  FAILED: 'error',
};

const TRIGGER_COLOR: Record<string, string> = {
  POST_SYNC: 'var(--accent-primary)',
  MANUAL: 'var(--status-info)',
  SCHEDULED: 'var(--text-secondary)',
  POLICY_CHANGE: 'var(--status-warning)',
};

const DECISION_COLOR: Record<string, string> = {
  CHANGE: 'success',
  SKIP: 'warning',
  HOLD: 'neutral',
};

@Component({
  selector: 'dp-run-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    KpiCardComponent,
    DataGridComponent,
    EmptyStateComponent,
    FilterBarComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <div class="flex h-full flex-col">
      <!-- Back button -->
      <div class="flex items-center gap-3 border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-6 py-2.5">
        <button
          (click)="goBack()"
          class="cursor-pointer rounded-[var(--radius-sm)] px-2 py-1 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
        >
          {{ 'pricing.runs.back' | translate }}
        </button>
        @if (run()) {
          <span class="text-sm font-medium text-[var(--text-primary)]">
            {{ 'pricing.runs.detail.run_number' | translate:{ id: run()!.id } }}
          </span>
        }
      </div>

      @if (runQuery.isPending()) {
        <div class="flex flex-1 items-center justify-center">
          <span
            class="dp-spinner inline-block h-8 w-8 rounded-full border-2 border-[var(--border-default)]"
            style="border-top-color: var(--accent-primary)"
          ></span>
        </div>
      } @else if (runQuery.isError()) {
        <div class="p-4">
          <dp-empty-state
            [message]="'pricing.runs.detail_error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="runQuery.refetch()"
          />
        </div>
      } @else if (run()) {
        <!-- KPI Strip -->
        <div class="flex gap-3 bg-[var(--bg-secondary)] px-6 py-3">
          <dp-kpi-card
            [label]="'pricing.runs.kpi.total' | translate"
            [value]="run()!.totalOffers"
            [loading]="false"
          />
          <dp-kpi-card
            [label]="'pricing.runs.kpi.eligible' | translate"
            [value]="run()!.eligibleCount"
            [loading]="false"
          />
          <dp-kpi-card
            [label]="'pricing.runs.kpi.changed' | translate"
            [value]="run()!.changeCount"
            [loading]="false"
          />
          <dp-kpi-card
            [label]="'pricing.runs.kpi.skipped' | translate"
            [value]="run()!.skipCount"
            [loading]="false"
          />
          <dp-kpi-card
            [label]="'pricing.runs.kpi.hold' | translate"
            [value]="run()!.holdCount"
            [loading]="false"
          />
        </div>

        <!-- Meta info -->
        <div class="flex flex-wrap items-center gap-3 border-b border-[var(--border-default)] px-6 py-2.5">
          <!-- Trigger badge -->
          <span
            class="inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-medium"
            [style.background-color]="'color-mix(in srgb, ' + triggerCssColor() + ' 12%, transparent)'"
            [style.color]="triggerCssColor()"
          >
            {{ triggerLabel() }}
          </span>

          <span class="text-[var(--text-tertiary)]">·</span>
          <span class="text-sm text-[var(--text-secondary)]">{{ run()!.connectionName }}</span>

          <span class="text-[var(--text-tertiary)]">·</span>

          <!-- Status badge -->
          <span
            class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
            [style.background-color]="'color-mix(in srgb, ' + statusCssVar() + ' 12%, transparent)'"
            [style.color]="statusCssVar()"
          >
            @if (run()!.status === 'IN_PROGRESS') {
              <span class="inline-block h-1.5 w-1.5 animate-pulse rounded-full" [style.background-color]="statusCssVar()"></span>
            } @else {
              <span class="inline-block h-1.5 w-1.5 rounded-full" [style.background-color]="statusCssVar()"></span>
            }
            {{ statusLabel() }}
          </span>

          <span class="text-[var(--text-tertiary)]">·</span>
          <span class="text-sm text-[var(--text-tertiary)]">{{ timingDisplay() }}</span>
        </div>

        @if (run()!.errorDetails) {
          <div class="mx-6 mt-3 rounded-[var(--radius-md)] border border-[var(--status-error)] bg-[color-mix(in_srgb,var(--status-error)_8%,transparent)] px-4 py-2.5 text-sm text-[var(--status-error)]">
            {{ run()!.errorDetails }}
          </div>
        }

        <!-- Decision filter -->
        <div class="border-b border-[var(--border-default)] px-6 py-2.5">
          <dp-filter-bar
            [filters]="decisionFilterConfigs"
            [values]="decisionFilterValues()"
            (filtersChanged)="onDecisionFiltersChanged($event)"
          />
        </div>

        <!-- Decision Grid -->
        <div class="flex-1 px-6 py-3">
          @if (decisionsQuery.isError()) {
            <dp-empty-state
              [message]="'pricing.decisions.error' | translate"
              [actionLabel]="'actions.retry' | translate"
              (action)="decisionsQuery.refetch()"
            />
          } @else if (!decisionsQuery.isPending() && decisionRows().length === 0) {
            <dp-empty-state
              [message]="'pricing.runs.detail.empty_decisions' | translate"
            />
          } @else {
            <dp-data-grid
              [columnDefs]="decisionColumnDefs"
              [rowData]="decisionRows()"
              [loading]="decisionsQuery.isPending()"
              [pagination]="true"
              [pageSize]="100"
              [getRowId]="getRowId"
              [height]="'100%'"
            />
          }
        </div>
      }
    </div>
  `,
})
export class RunDetailPageComponent {
  readonly runId = input.required<string>();

  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  private readonly translate = inject(TranslateService);

  readonly decisionFilterValues = signal<Record<string, any>>({});
  readonly decisionPage = signal(0);

  private readonly numericRunId = computed(() => Number(this.runId()));

  readonly runQuery = injectQuery(() => ({
    queryKey: ['pricing-run', this.numericRunId()],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.getRunDetail(this.wsStore.currentWorkspaceId()!, this.numericRunId()),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !isNaN(this.numericRunId()),
    staleTime: 15_000,
    refetchInterval: 30_000,
  }));

  readonly run = computed(() => this.runQuery.data() ?? null);
  readonly statusLabel = computed(() => {
    const status = this.run()?.status;
    return status ? this.translate.instant(`pricing.runs.status.${status}`) : '';
  });
  readonly triggerLabel = computed(() => {
    const trigger = this.run()?.triggerType;
    return trigger ? this.translate.instant(`pricing.runs.trigger.${trigger}`) : '';
  });

  readonly statusCssVar = computed(() => {
    const color = RUN_STATUS_COLOR[this.run()?.status ?? ''] ?? 'neutral';
    return `var(--status-${color})`;
  });

  readonly triggerCssColor = computed(() =>
    TRIGGER_COLOR[this.run()?.triggerType ?? ''] ?? 'var(--text-secondary)',
  );

  readonly timingDisplay = computed(() => {
    const r = this.run();
    if (!r) return '';
    const started = this.formatTimestamp(r.startedAt);
    const duration = this.formatDuration(r.startedAt, r.completedAt);
    if (r.status === 'IN_PROGRESS') return this.translate.instant('pricing.runs.detail.started', { time: started });
    if (r.completedAt) return `${started} · ${duration}`;
    return started;
  });

  private readonly decisionFilter = computed<PricingDecisionFilter>(() => {
    const vals = this.decisionFilterValues();
    const f: PricingDecisionFilter = { pricingRunId: this.numericRunId() };
    if (vals['decisionType']?.length) f.decisionType = vals['decisionType'];
    return f;
  });

  readonly decisionsQuery = injectQuery(() => ({
    queryKey: ['pricing-decisions', this.wsStore.currentWorkspaceId(), this.numericRunId(), this.decisionFilter(), this.decisionPage()],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.listDecisions(
          this.wsStore.currentWorkspaceId()!,
          this.decisionFilter(),
          this.decisionPage(),
          100,
          'decisionType,asc',
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !isNaN(this.numericRunId()),
    staleTime: 30_000,
  }));

  readonly decisionRows = computed(() => this.decisionsQuery.data()?.content ?? []);

  readonly decisionFilterConfigs: FilterConfig[] = [
    {
      key: 'decisionType',
      label: 'pricing.runs.detail.filter.decision_type',
      type: 'multi-select',
      options: (['CHANGE', 'SKIP', 'HOLD'] as const).map(value => ({
        value,
        label: `pricing.decisions.type.${value}`,
      })),
    },
  ];

  readonly decisionColumnDefs = [
    {
      headerName: this.translate.instant('pricing.runs.detail.col.offer'),
      field: 'offerName',
      minWidth: 200,
      flex: 1,
      sortable: true,
    },
    {
      headerName: this.translate.instant('pricing.runs.detail.col.sku'),
      field: 'sellerSku',
      width: 120,
      sortable: true,
      cellClass: 'font-mono text-[length:var(--text-sm)]',
    },
    {
      headerName: this.translate.instant('pricing.runs.detail.col.decision'),
      field: 'decisionType',
      width: 120,
      sortable: true,
      cellRenderer: (params: any) => {
        const dt = params.value as string;
        const label = this.translate.instant(`pricing.decisions.type.${dt}`);
        const color = DECISION_COLOR[dt] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        return `<span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                  style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}">
          <span class="inline-block h-1.5 w-1.5 rounded-full" style="background-color: ${cssVar}"></span>
          ${label}
        </span>`;
      },
    },
    {
      headerName: this.translate.instant('pricing.runs.detail.col.current_price'),
      field: 'currentPrice',
      width: 120,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: (params: any) => this.formatPrice(params.value),
    },
    {
      headerName: this.translate.instant('pricing.runs.detail.col.target_price'),
      field: 'targetPrice',
      width: 120,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: (params: any) => this.formatPrice(params.value),
    },
    {
      headerName: this.translate.instant('pricing.runs.detail.col.price_delta'),
      field: 'changePct',
      width: 90,
      sortable: true,
      cellClass: 'font-mono text-right',
      cellRenderer: (params: any) => {
        const v = params.value;
        if (v === null || v === undefined) return '—';
        const abs = Math.abs(v).toFixed(1).replace('.', ',');
        if (v > 0) return `<span style="color: var(--finance-positive)">↑ ${abs}%</span>`;
        if (v < 0) return `<span style="color: var(--finance-negative)">↓ ${abs}%</span>`;
        return `<span style="color: var(--finance-zero)">→ 0%</span>`;
      },
    },
    {
      headerName: this.translate.instant('pricing.runs.detail.col.policy'),
      field: 'policyName',
      width: 160,
      sortable: true,
    },
    {
      headerName: this.translate.instant('pricing.runs.detail.col.strategy'),
      field: 'strategyType',
      width: 140,
      sortable: true,
      cellRenderer: (params: any) => {
        const st = params.value as string;
        const label = this.translate.instant(`pricing.policies.strategy.${st}`);
        return `<span class="inline-flex items-center rounded-full border border-[var(--border-default)] px-2 py-0.5 text-[11px] text-[var(--text-secondary)]">
          ${label}
        </span>`;
      },
    },
    {
      headerName: this.translate.instant('pricing.runs.detail.col.skip_reason'),
      field: 'skipReason',
      width: 200,
      sortable: false,
      cellClass: 'text-[length:var(--text-sm)] text-[color:var(--text-tertiary)]',
      valueFormatter: (params: any) => {
        if (!params.value) return '—';
        return this.translate.instant(params.value);
      },
    },
  ];

  readonly getRowId = (params: any) => String(params.data.id);

  goBack(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'pricing', 'runs']);
  }

  onDecisionFiltersChanged(values: Record<string, any>): void {
    this.decisionFilterValues.set(values);
    this.decisionPage.set(0);
  }

  private formatPrice(value: number | null): string {
    return formatMoney(value, 0);
  }

  private formatTimestamp(iso: string | null): string {
    return formatDateTime(iso, 'full');
  }

  private formatDuration(start: string | null, end: string | null): string {
    if (!start) return '—';
    const endMs = end ? new Date(end).getTime() : Date.now();
    const ms = endMs - new Date(start).getTime();
    if (ms < 0) return '—';
    const totalSec = Math.floor(ms / 1000);
    const secUnit = this.translate.instant('common.time.sec');
    if (totalSec < 60) return `${totalSec} ${secUnit}`;
    const min = Math.floor(totalSec / 60);
    const sec = totalSec % 60;
    const minUnit = this.translate.instant('common.time.min');
    return sec > 0 ? `${min} ${minUnit} ${sec} ${secUnit}` : `${min} ${minUnit}`;
  }
}
