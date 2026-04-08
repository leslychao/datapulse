import {
  ChangeDetectionStrategy,
  Component,
  computed,
  HostListener,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom, startWith } from 'rxjs';

import { PricingApiService } from '@core/api/pricing-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { PricingRunSummary, PricingRunFilter } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import {
  FilterBarComponent,
  FilterConfig,
} from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import {
  formatRelativeTime,
  formatMoney,
  renderBadge,
} from '@shared/utils/format.utils';
import { createListPageState } from '@shared/utils/list-page-state';

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
  CHANGE: 'var(--status-success)',
  SKIP: 'var(--status-warning)',
  HOLD: 'var(--text-tertiary)',
};

@Component({
  selector: 'dp-runs-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    RouterLink,
    FilterBarComponent,
    DataGridComponent,
    EmptyStateComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <div class="flex h-full flex-col">
      <!-- Toolbar -->
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <h2 class="text-sm font-semibold text-[var(--text-primary)]">
          {{ 'pricing.runs.title' | translate }}
        </h2>
        @if (rbac.canWritePolicies()) {
          <button
            (click)="triggerRun()"
            [disabled]="triggerMutation.isPending()"
            class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
          >
            @if (triggerMutation.isPending()) {
              {{ 'pricing.runs.trigger_submitting' | translate }}
            } @else {
              {{ 'pricing.runs.trigger_run' | translate }}
            }
          </button>
        }
      </div>

      <!-- Filter Bar -->
      <div class="border-b border-[var(--border-default)] px-4 py-2">
        <dp-filter-bar
          [filters]="filterConfigs"
          [values]="listState.filterValues()"
          (filtersChanged)="onFiltersChanged($event)"
        />
      </div>

      <!-- Content: grid + peek panel -->
      <div class="flex flex-1 flex-col overflow-hidden">
        <div class="flex-1 px-4 py-2" [class.max-h-[50%]]="expandedRun()">
          @if (runsQuery.isError()) {
            <dp-empty-state
              [message]="'pricing.runs.error' | translate"
              [actionLabel]="'actions.retry' | translate"
              (action)="runsQuery.refetch()"
            />
          } @else if (!runsQuery.isPending() && rows().length === 0) {
            <dp-empty-state
              [message]="listState.hasActiveFilters()
                ? ('pricing.runs.empty_filtered' | translate)
                : ('pricing.runs.empty' | translate)"
              [actionLabel]="listState.hasActiveFilters() ? ('filter_bar.reset_all' | translate) : ''"
              (action)="onFiltersChanged({})"
            />
          } @else {
            <dp-data-grid
              [columnDefs]="columnDefs()"
              [rowData]="rows()"
              [loading]="runsQuery.isPending()"
              [pagination]="true"
              [pageSize]="50"
              [getRowId]="getRowId"
              [height]="'100%'"
              [initialSortModel]="listState.initialSortModel()"
              (sortChanged)="onSortChanged($event)"
            />
          }
        </div>

        <!-- Peek panel -->
        @if (expandedRun(); as run) {
          <div class="peek-section shrink-0 border-t-2 border-t-[var(--accent-primary)] bg-[var(--bg-secondary)] overflow-y-auto"
               style="max-height: 50%">
            <!-- Peek header -->
            <div class="flex items-center justify-between px-4 py-2">
              <div class="flex items-center gap-2 text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                <span class="font-mono font-medium text-[var(--text-secondary)]">
                  {{ 'pricing.runs.detail.run_number' | translate:{ id: run.id } }}
                </span>
                <span>·</span>
                <span>{{ run.connectionName }}</span>
                @if (formatDuration(run.startedAt, run.completedAt) !== '—') {
                  <span>·</span>
                  <span>{{ formatDuration(run.startedAt, run.completedAt) }}</span>
                }
              </div>
              <div class="flex items-center gap-3">
                <a
                  class="text-[length:var(--text-xs)] font-medium text-[var(--accent-primary)] transition-colors hover:underline"
                  [routerLink]="['/workspace', wsStore.currentWorkspaceId(), 'pricing', 'runs', run.id]"
                >
                  {{ 'pricing.runs.peek.open_full' | translate }}
                </a>
                <button
                  type="button"
                  (click)="closeExpand()"
                  class="flex h-6 w-6 items-center justify-center rounded-[var(--radius-sm)] text-[var(--text-tertiary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
                >
                  <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M18 6 6 18"/><path d="m6 6 12 12"/>
                  </svg>
                </button>
              </div>
            </div>

            <!-- KPI strip -->
            <div class="flex gap-6 border-t border-[var(--border-subtle)] px-4 py-2">
              <div class="flex items-baseline gap-1.5">
                <span class="font-mono text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]">{{ run.totalOffers }}</span>
                <span class="text-[11px] text-[var(--text-tertiary)]">{{ 'pricing.runs.kpi.total' | translate }}</span>
              </div>
              <div class="flex items-baseline gap-1.5">
                <span class="font-mono text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]">{{ run.eligibleCount }}</span>
                <span class="text-[11px] text-[var(--text-tertiary)]">{{ 'pricing.runs.kpi.eligible' | translate }}</span>
              </div>
              <div class="flex items-baseline gap-1.5">
                <span class="font-mono text-[length:var(--text-lg)] font-semibold text-[var(--status-success)]">{{ run.changeCount }}</span>
                <span class="text-[11px] text-[var(--text-tertiary)]">{{ 'pricing.runs.kpi.changed' | translate }}</span>
              </div>
              <div class="flex items-baseline gap-1.5">
                <span class="font-mono text-[length:var(--text-lg)] font-semibold text-[var(--status-warning)]">{{ run.skipCount }}</span>
                <span class="text-[11px] text-[var(--text-tertiary)]">{{ 'pricing.runs.kpi.skipped' | translate }}</span>
              </div>
              <div class="flex items-baseline gap-1.5">
                <span class="font-mono text-[length:var(--text-lg)] font-semibold text-[var(--text-secondary)]">{{ run.holdCount }}</span>
                <span class="text-[11px] text-[var(--text-tertiary)]">{{ 'pricing.runs.kpi.hold' | translate }}</span>
              </div>
            </div>

            <!-- Decisions mini-table -->
            <div class="border-t border-[var(--border-subtle)] px-4 py-2.5">
              @if (peekDecisionsQuery.isPending()) {
                <div class="space-y-2.5">
                  @for (i of [1,2,3]; track i) {
                    <div class="flex items-center gap-4">
                      <div class="h-4 w-36 rounded dp-shimmer"></div>
                      <div class="h-4 w-16 rounded dp-shimmer"></div>
                      <div class="h-5 w-20 rounded-full dp-shimmer"></div>
                      <div class="h-4 w-14 rounded dp-shimmer"></div>
                      <div class="h-4 w-14 rounded dp-shimmer"></div>
                      <div class="h-4 w-10 rounded dp-shimmer"></div>
                    </div>
                  }
                </div>
              } @else if (peekDecisions().length === 0) {
                <p class="py-2 text-[length:var(--text-sm)] text-[var(--text-tertiary)]">
                  {{ 'pricing.runs.peek.no_decisions' | translate }}
                </p>
              } @else {
                <table class="w-full">
                  <thead>
                    <tr>
                      <th class="pb-1.5 pr-3 text-left text-[11px] font-medium uppercase tracking-wider text-[var(--text-tertiary)]">
                        {{ 'pricing.runs.detail.col.offer' | translate }}
                      </th>
                      <th class="pb-1.5 pr-3 text-left text-[11px] font-medium uppercase tracking-wider text-[var(--text-tertiary)]">
                        {{ 'pricing.runs.detail.col.sku' | translate }}
                      </th>
                      <th class="pb-1.5 pr-3 text-left text-[11px] font-medium uppercase tracking-wider text-[var(--text-tertiary)]">
                        {{ 'pricing.runs.detail.col.decision' | translate }}
                      </th>
                      <th class="pb-1.5 pr-3 text-right text-[11px] font-medium uppercase tracking-wider text-[var(--text-tertiary)]">
                        {{ 'pricing.runs.detail.col.current_price' | translate }}
                      </th>
                      <th class="pb-1.5 pr-3 text-right text-[11px] font-medium uppercase tracking-wider text-[var(--text-tertiary)]">
                        {{ 'pricing.runs.detail.col.target_price' | translate }}
                      </th>
                      <th class="pb-1.5 pr-3 text-right text-[11px] font-medium uppercase tracking-wider text-[var(--text-tertiary)]">
                        {{ 'pricing.runs.detail.col.price_delta' | translate }}
                      </th>
                      <th class="pb-1.5 text-left text-[11px] font-medium uppercase tracking-wider text-[var(--text-tertiary)]">
                        {{ 'pricing.runs.detail.col.skip_reason' | translate }}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (d of peekDecisions(); track d.id) {
                      <tr class="border-t border-[var(--border-subtle)]">
                        <td class="max-w-[200px] truncate py-1.5 pr-3 text-[length:var(--text-sm)] text-[var(--text-primary)]">
                          {{ d.offerName }}
                        </td>
                        <td class="py-1.5 pr-3 font-mono text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                          {{ d.sellerSku }}
                        </td>
                        <td class="py-1.5 pr-3">
                          <span
                            class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium"
                            [style.color]="decisionColor(d.decisionType)"
                            [style.background-color]="decisionBg(d.decisionType)"
                          >
                            <span class="inline-block h-1.5 w-1.5 rounded-full" [style.background-color]="decisionColor(d.decisionType)"></span>
                            {{ 'pricing.decisions.type.' + d.decisionType | translate }}
                          </span>
                          @if (d.executionMode === 'SIMULATED') {
                            <span class="ml-1 inline-flex items-center rounded-full bg-[color-mix(in_srgb,var(--status-info)_12%,transparent)] px-1.5 py-0.5 text-[10px] font-medium text-[var(--status-info)]">
                              {{ 'pricing.execution_mode.sim_badge' | translate }}
                            </span>
                          }
                        </td>
                        <td class="py-1.5 pr-3 text-right font-mono text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                          {{ formatPrice(d.currentPrice) }}
                        </td>
                        <td class="py-1.5 pr-3 text-right font-mono text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                          {{ formatPrice(d.targetPrice) }}
                        </td>
                        <td
                          class="py-1.5 pr-3 text-right font-mono text-[length:var(--text-sm)]"
                          [style.color]="changePctColor(d.changePct)"
                        >
                          {{ changePctText(d.changePct) }}
                        </td>
                        <td class="max-w-[180px] truncate py-1.5 text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                          @if (d.skipReason) {
                            {{ d.skipReason | translate }}
                          } @else {
                            —
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>

                @if (peekDecisionsTotalCount() > peekDecisions().length) {
                  <p class="mt-2 text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                    {{ 'pricing.runs.peek.shown_of' | translate:{ shown: peekDecisions().length, total: peekDecisionsTotalCount() } }}
                  </p>
                }
              }
            </div>
          </div>
        }
      </div>
    </div>

  `,
  styles: [`
    .peek-section {
      animation: peekReveal 200ms ease-out;
    }
    @keyframes peekReveal {
      from { opacity: 0; transform: translateY(4px); }
      to { opacity: 1; transform: translateY(0); }
    }
  `],
})
export class RunsListPageComponent {
  private readonly pricingApi = inject(PricingApiService);
  protected readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);
  protected readonly rbac = inject(RbacService);

  readonly listState = createListPageState({
    defaultSort: { column: 'createdAt', direction: 'desc' },
    defaultPageSize: 50,
    filterBarDefs: [
      { key: 'status', type: 'csv' },
      { key: 'triggerType', type: 'csv' },
      { key: 'period', type: 'date-range' },
    ],
  });

  readonly expandedRunId = signal<number | null>(null);

  private readonly translationChange = toSignal(
    this.translate.onTranslationChange.pipe(startWith(null)),
  );

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: 'pricing.runs.filter.status',
      type: 'multi-select',
      options: (['PENDING', 'IN_PROGRESS', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'] as const)
        .map((value) => ({ value, label: `pricing.runs.status.${value}` })),
    },
    {
      key: 'triggerType',
      label: 'pricing.runs.filter.trigger',
      type: 'multi-select',
      options: (['POST_SYNC', 'MANUAL', 'SCHEDULED', 'POLICY_CHANGE'] as const)
        .map((value) => ({ value, label: `pricing.runs.trigger.${value}` })),
    },
    {
      key: 'period',
      label: 'pricing.runs.filter.period',
      type: 'date-range',
    },
  ];

  readonly columnDefs = computed(() => {
    this.translationChange();
    return [
      {
        headerName: '#',
        field: 'id',
        width: 70,
        sortable: true,
        cellClass: 'font-mono text-right',
        cellRenderer: (params: { value: unknown }) => {
          if (params.value == null || params.value === '') return '';
          return `<span class="font-medium text-[var(--accent-primary)] cursor-pointer hover:underline">${params.value}</span>`;
        },
        onCellClicked: (params: { data?: PricingRunSummary }) => {
          if (params.data) {
            this.onRowClicked(params.data);
          }
        },
      },
      {
        headerName: this.translate.instant('pricing.runs.col.trigger'),
        headerTooltip: this.translate.instant('pricing.runs.col.trigger'),
        field: 'triggerType',
        width: 130,
        sortable: true,
        cellRenderer: (params: any) => {
          if (!params.value) return '';
          const color = TRIGGER_COLOR[params.value] ?? 'var(--text-secondary)';
          const label = this.translate.instant('pricing.runs.trigger.' + params.value);
          return renderBadge(label, color);
        },
      },
      {
        headerName: this.translate.instant('pricing.runs.col.connection'),
        field: 'connectionName',
        width: 180,
        sortable: true,
      },
      {
        headerName: this.translate.instant('pricing.runs.col.status'),
        field: 'status',
        width: 170,
        sortable: true,
        cellRenderer: (params: any) => {
          if (!params.value) return '';
          const color = RUN_STATUS_COLOR[params.value] ?? 'neutral';
          const label = this.translate.instant('pricing.runs.status.' + params.value);
          let html = renderBadge(label, `var(--status-${color})`, params.value === 'IN_PROGRESS');
          const run = params.data as PricingRunSummary | undefined;
          if (run && run.simulatedDecisionCount > 0) {
            html += ` <span class="ml-1 inline-flex items-center rounded-full bg-[color-mix(in_srgb,var(--status-info)_12%,transparent)] px-1.5 py-0.5 text-[10px] font-medium text-[var(--status-info)]">SIM</span>`;
          }
          return html;
        },
      },
      {
        headerName: this.translate.instant('pricing.runs.col.total'),
        headerTooltip: this.translate.instant('pricing.runs.col.total'),
        field: 'totalOffers',
        width: 80,
        sortable: true,
        cellClass: 'font-mono text-right',
      },
      {
        headerName: this.translate.instant('pricing.runs.col.change'),
        headerTooltip: this.translate.instant('pricing.runs.col.change'),
        field: 'changeCount',
        width: 80,
        sortable: true,
        cellClass: 'font-mono text-right',
        cellStyle: { color: 'var(--status-success)' },
      },
      {
        headerName: this.translate.instant('pricing.runs.col.skip'),
        headerTooltip: this.translate.instant('pricing.runs.col.skip'),
        field: 'skipCount',
        width: 80,
        sortable: true,
        cellClass: 'font-mono text-right',
        cellStyle: { color: 'var(--status-warning)' },
      },
      {
        headerName: this.translate.instant('pricing.runs.col.duration'),
        headerTooltip: this.translate.instant('pricing.runs.col.duration'),
        field: '_duration',
        width: 100,
        sortable: false,
        cellClass: 'font-mono text-right',
        valueGetter: (params: any) => {
          if (!params.data) return '';
          return this.formatDuration(params.data.startedAt, params.data.completedAt);
        },
      },
      {
        headerName: this.translate.instant('pricing.runs.col.created_at'),
        headerTooltip: this.translate.instant('pricing.runs.col.created_at'),
        field: 'createdAt',
        width: 120,
        sortable: true,
        valueFormatter: (params: any) => formatRelativeTime(params.value),
      },
    ];
  });

  private readonly filter = computed<PricingRunFilter>(() => {
    const vals = this.listState.filterValues();
    const f: PricingRunFilter = {};
    if (vals['status']?.length) f.status = vals['status'];
    if (vals['triggerType']?.length) f.triggerType = vals['triggerType'];
    if (vals['period']?.from) f.from = vals['period'].from;
    if (vals['period']?.to) f.to = vals['period'].to;
    return f;
  });

  readonly runsQuery = injectQuery(() => ({
    queryKey: [
      'pricing-runs',
      this.wsStore.currentWorkspaceId(),
      this.filter(),
      this.listState.sortParam(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.listRuns(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          0,
          50,
          this.listState.sortParam(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly peekDecisionsQuery = injectQuery(() => ({
    queryKey: [
      'pricing-decisions',
      'peek',
      this.wsStore.currentWorkspaceId(),
      this.expandedRunId(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.listDecisions(
          this.wsStore.currentWorkspaceId()!,
          { pricingRunId: this.expandedRunId()! },
          0,
          10,
          'decisionType,asc',
        ),
      ),
    enabled:
      this.expandedRunId() != null &&
      !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.runsQuery.data()?.content ?? []);

  readonly expandedRun = computed(() => {
    const id = this.expandedRunId();
    if (id == null) return null;
    return this.rows().find((r) => r.id === id) ?? null;
  });

  readonly peekDecisions = computed(
    () => this.peekDecisionsQuery.data()?.content ?? [],
  );

  readonly peekDecisionsTotalCount = computed(
    () => this.peekDecisionsQuery.data()?.totalElements ?? 0,
  );

  readonly triggerMutation = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(
        this.pricingApi.triggerManualRuns(
          this.wsStore.currentWorkspaceId()!,
        ),
      ),
    onSuccess: (runs: PricingRunSummary[]) => {
      this.queryClient.invalidateQueries({ queryKey: ['pricing-runs'] });
      this.toast.success(
        this.translate.instant('pricing.runs.trigger_success', { count: runs.length }),
      );
    },
    onError: () => {
      this.toast.error(this.translate.instant('pricing.runs.trigger_error'));
    },
  }));

  readonly getRowId = (params: any) => String(params.data.id);

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.expandedRunId() !== null) {
      this.closeExpand();
    }
  }

  onRowClicked(run: PricingRunSummary): void {
    this.expandedRunId.set(
      this.expandedRunId() === run.id ? null : run.id,
    );
  }

  onSortChanged(sort: { column: string; direction: string }): void {
    this.listState.onSortChanged(sort);
    this.expandedRunId.set(null);
  }

  closeExpand(): void {
    this.expandedRunId.set(null);
  }

  onFiltersChanged(values: Record<string, any>): void {
    this.listState.onFiltersChanged(values);
    this.expandedRunId.set(null);
  }

  triggerRun(): void {
    this.triggerMutation.mutate(undefined);
  }

  decisionColor(dt: string): string {
    return DECISION_COLOR[dt] ?? 'var(--text-tertiary)';
  }

  decisionBg(dt: string): string {
    return `color-mix(in srgb, ${this.decisionColor(dt)} 12%, transparent)`;
  }

  changePctText(value: number | null): string {
    if (value === null || value === undefined) return '—';
    const abs = Math.abs(value).toFixed(1).replace('.', ',');
    if (value > 0) return `+${abs}%`;
    if (value < 0) return `−${abs}%`;
    return '0%';
  }

  changePctColor(value: number | null): string {
    if (value === null || value === undefined) return 'var(--text-tertiary)';
    if (value > 0) return 'var(--finance-positive)';
    if (value < 0) return 'var(--finance-negative)';
    return 'var(--finance-zero)';
  }

  formatDuration(start: string | null, end: string | null): string {
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
    return sec > 0
      ? `${min} ${minUnit} ${sec} ${secUnit}`
      : `${min} ${minUnit}`;
  }

  formatPrice(value: number | null): string {
    return formatMoney(value, 0);
  }
}
