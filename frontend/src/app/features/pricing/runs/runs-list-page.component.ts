import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PricingApiService } from '@core/api/pricing-api.service';
import { formatRelativeTime } from '@shared/utils/format.utils';
import { ConnectionApiService } from '@core/api/connection-api.service';
import {
  PricingRunSummary,
  PricingRunFilter,
  ConnectionSummary,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';

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

@Component({
  selector: 'dp-runs-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    FormsModule,
    FilterBarComponent,
    DataGridComponent,
    EmptyStateComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <div class="flex h-full flex-col">
      <!-- Toolbar -->
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-6 py-2.5">
        <h2 class="text-sm font-semibold text-[var(--text-primary)]">
          {{ 'pricing.runs.title' | translate }}
        </h2>
        <button
          (click)="showTriggerModal.set(true)"
          class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
        >
          {{ 'pricing.runs.trigger_run' | translate }}
        </button>
      </div>

      <!-- Filter Bar -->
      <div class="border-b border-[var(--border-default)] px-6 py-2.5">
        <dp-filter-bar
          [filters]="filterConfigs"
          [values]="filterValues()"
          (filtersChanged)="onFiltersChanged($event)"
        />
      </div>

      <!-- Data Grid -->
      <div class="flex-1 px-6 py-3">
        @if (runsQuery.isError()) {
          <dp-empty-state
            [message]="'pricing.runs.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="runsQuery.refetch()"
          />
        } @else if (!runsQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="hasActiveFilters()
              ? ('pricing.runs.empty_filtered' | translate)
              : ('pricing.runs.empty' | translate)"
            [actionLabel]="hasActiveFilters() ? ('filter_bar.reset_all' | translate) : ''"
            (action)="onFiltersChanged({})"
          />
        } @else {
          <dp-data-grid
            [columnDefs]="columnDefs"
            [rowData]="rows()"
            [loading]="runsQuery.isPending()"
            [pagination]="true"
            [pageSize]="50"
            [getRowId]="getRowId"
            [height]="'100%'"
            (rowClicked)="onRowClicked($event)"
          />
        }
      </div>
    </div>

    <!-- Trigger Manual Run Modal -->
    @if (showTriggerModal()) {
      <div class="fixed inset-0 z-[9000] flex items-center justify-center">
        <div class="absolute inset-0 bg-[var(--bg-overlay)]" (click)="showTriggerModal.set(false)"></div>
        <div
          class="relative z-10 w-full max-w-sm rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-6 shadow-[var(--shadow-md)] animate-[fadeIn_150ms_ease]"
        >
          <h3 class="text-base font-semibold text-[var(--text-primary)]">{{ 'pricing.runs.trigger_run' | translate }}</h3>
          <p class="mt-1 text-sm text-[var(--text-secondary)]">
            {{ 'pricing.runs.trigger_modal.description' | translate }}
          </p>

          <div class="mt-4 flex flex-col gap-1.5">
            <label class="text-[11px] text-[var(--text-tertiary)]">{{ 'pricing.runs.trigger_modal.connection_label' | translate }}</label>
            @if (connectionsQuery.isPending()) {
              <div class="h-9 rounded-[var(--radius-md)] bg-[var(--bg-tertiary)] dp-shimmer"></div>
            } @else {
              <select
                [(ngModel)]="selectedConnectionId"
                class="h-8 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
              >
                <option [ngValue]="null" disabled>{{ 'pricing.runs.trigger_modal.connection_placeholder' | translate }}</option>
                @for (conn of activeConnections(); track conn.id) {
                  <option [ngValue]="conn.id">{{ conn.name }} ({{ conn.marketplaceType }})</option>
                }
              </select>
            }
          </div>

          <div class="mt-6 flex justify-end gap-3">
            <button
              (click)="showTriggerModal.set(false)"
              class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm font-medium text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              {{ 'actions.cancel' | translate }}
            </button>
            <button
              (click)="triggerRun()"
              [disabled]="!selectedConnectionId || triggerMutation.isPending()"
              class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              @if (triggerMutation.isPending()) {
                {{ 'pricing.runs.trigger_modal.submitting' | translate }}
              } @else {
                {{ 'pricing.runs.trigger_modal.submit' | translate }}
              }
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    @keyframes fadeIn {
      from { opacity: 0; transform: scale(0.97); }
      to { opacity: 1; transform: scale(1); }
    }
  `],
})
export class RunsListPageComponent {
  private readonly pricingApi = inject(PricingApiService);
  private readonly connectionApi = inject(ConnectionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);

  readonly filterValues = signal<Record<string, any>>({});
  readonly showTriggerModal = signal(false);
  readonly currentPage = signal(0);
  readonly currentSort = signal('createdAt,desc');
  protected selectedConnectionId: number | null = null;

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: 'pricing.runs.filter.status',
      type: 'multi-select',
      options: (['PENDING', 'IN_PROGRESS', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'] as const).map(value => ({
        value,
        label: `pricing.runs.status.${value}`,
      })),
    },
    {
      key: 'triggerType',
      label: 'pricing.runs.filter.trigger',
      type: 'multi-select',
      options: (['POST_SYNC', 'MANUAL', 'SCHEDULED', 'POLICY_CHANGE'] as const).map(value => ({
        value,
        label: `pricing.runs.trigger.${value}`,
      })),
    },
    {
      key: 'period',
      label: 'pricing.runs.filter.period',
      type: 'date-range',
    },
  ];

  readonly columnDefs = [
    {
      headerName: '#',
      field: 'id',
      width: 70,
      sortable: true,
      cellClass: 'font-mono text-right',
    },
    {
      headerName: this.translate.instant('pricing.runs.col.trigger'),
      field: 'triggerType',
      width: 130,
      sortable: true,
      cellRenderer: (params: any) => {
        const t = params.value as string;
        const label = this.translate.instant(`pricing.runs.trigger.${t}`);
        const color = TRIGGER_COLOR[t] ?? 'var(--text-secondary)';
        return `<span class="inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                  style="background-color: color-mix(in srgb, ${color} 12%, transparent); color: ${color}">
          ${label}
        </span>`;
      },
    },
    {
      headerName: this.translate.instant('pricing.runs.col.connection'),
      field: 'connectionName',
      minWidth: 160,
      flex: 1,
      sortable: true,
    },
    {
      headerName: this.translate.instant('pricing.runs.col.status'),
      field: 'status',
      width: 140,
      sortable: true,
      cellRenderer: (params: any) => {
        const st = params.value as string;
        const label = this.translate.instant(`pricing.runs.status.${st}`);
        const color = RUN_STATUS_COLOR[st] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        const dotHtml = st === 'IN_PROGRESS'
          ? `<span class="inline-block h-1.5 w-1.5 rounded-full animate-pulse" style="background-color: ${cssVar}"></span>`
          : `<span class="inline-block h-1.5 w-1.5 rounded-full" style="background-color: ${cssVar}"></span>`;
        return `<span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                  style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}">
          ${dotHtml}
          ${label}
        </span>`;
      },
    },
    {
      headerName: this.translate.instant('pricing.runs.col.total'),
      field: 'totalOffers',
      width: 80,
      sortable: true,
      cellClass: 'font-mono text-right',
    },
    {
      headerName: this.translate.instant('pricing.runs.col.eligible'),
      field: 'eligibleCount',
      width: 90,
      sortable: true,
      cellClass: 'font-mono text-right',
    },
    {
      headerName: this.translate.instant('pricing.runs.col.change'),
      field: 'changeCount',
      width: 100,
      sortable: true,
      cellClass: 'font-mono text-right',
      cellStyle: () => ({ color: 'var(--status-success)' }),
    },
    {
      headerName: this.translate.instant('pricing.runs.col.skip'),
      field: 'skipCount',
      width: 90,
      sortable: true,
      cellClass: 'font-mono text-right',
      cellStyle: () => ({ color: 'var(--status-warning)' }),
    },
    {
      headerName: this.translate.instant('pricing.runs.col.hold'),
      field: 'holdCount',
      width: 90,
      sortable: true,
      cellClass: 'font-mono text-right',
      cellStyle: () => ({ color: 'var(--text-tertiary)' }),
    },
    {
      headerName: this.translate.instant('pricing.runs.col.duration'),
      field: 'startedAt',
      width: 110,
      sortable: false,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: any) => {
        if (!params.data) return '';
        return this.formatDuration(params.data.startedAt, params.data.completedAt);
      },
    },
    {
      headerName: this.translate.instant('pricing.runs.col.created_at'),
      field: 'createdAt',
      width: 120,
      sortable: true,
      sort: 'desc' as const,
      valueFormatter: (params: any) => this.formatRelativeTime(params.value),
    },
  ];

  private readonly filter = computed<PricingRunFilter>(() => {
    const vals = this.filterValues();
    const f: PricingRunFilter = {};
    if (vals['status']?.length) f.status = vals['status'];
    if (vals['triggerType']?.length) f.triggerType = vals['triggerType'];
    if (vals['period']?.from) f.from = vals['period'].from;
    if (vals['period']?.to) f.to = vals['period'].to;
    return f;
  });

  readonly runsQuery = injectQuery(() => ({
    queryKey: ['pricing-runs', this.wsStore.currentWorkspaceId(), this.filter(), this.currentPage(), this.currentSort()],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.listRuns(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.currentPage(),
          50,
          this.currentSort(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
    refetchInterval: 60_000,
  }));

  readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections'],
    queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
    enabled: this.showTriggerModal(),
    staleTime: 60_000,
  }));

  readonly rows = computed(() => this.runsQuery.data()?.content ?? []);

  readonly activeConnections = computed<ConnectionSummary[]>(() =>
    (this.connectionsQuery.data() ?? []).filter((c) => c.status === 'ACTIVE'),
  );

  readonly hasActiveFilters = computed(() =>
    Object.values(this.filterValues()).some(
      (v) => v !== '' && v !== null && v !== undefined && (!Array.isArray(v) || v.length > 0),
    ),
  );

  readonly getRowId = (params: any) => String(params.data.id);

  readonly triggerMutation = injectMutation(() => ({
    mutationFn: (connectionId: number) =>
      lastValueFrom(
        this.pricingApi.triggerManualRun(this.wsStore.currentWorkspaceId()!, connectionId),
      ),
    onSuccess: () => {
      this.showTriggerModal.set(false);
      this.selectedConnectionId = null;
      this.queryClient.invalidateQueries({ queryKey: ['pricing-runs'] });
      this.toast.success(this.translate.instant('pricing.runs.trigger_success'));
    },
    onError: () => {
      this.toast.error(this.translate.instant('pricing.runs.trigger_error'));
    },
  }));

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
    this.currentPage.set(0);
  }

  onRowClicked(row: PricingRunSummary): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'pricing', 'runs', row.id]);
  }

  triggerRun(): void {
    if (this.selectedConnectionId) {
      this.triggerMutation.mutate(this.selectedConnectionId);
    }
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

  private formatRelativeTime(iso: string | null): string {
    return formatRelativeTime(iso);
  }
}
