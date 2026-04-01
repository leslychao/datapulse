import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PricingApiService } from '@core/api/pricing-api.service';
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

const RUN_STATUS_LABEL: Record<string, string> = {
  PENDING: 'Ожидает',
  IN_PROGRESS: 'Выполняется',
  COMPLETED: 'Завершён',
  COMPLETED_WITH_ERRORS: 'С ошибками',
  FAILED: 'Ошибка',
};

const TRIGGER_LABEL: Record<string, string> = {
  POST_SYNC: 'После синхр.',
  MANUAL: 'Ручной',
  SCHEDULED: 'По расписанию',
  POLICY_CHANGE: 'Изм. политики',
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
  template: `
    <div class="flex h-full flex-col">
      <!-- Toolbar -->
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2.5">
        <h2 class="text-sm font-semibold text-[var(--text-primary)]">
          {{ 'pricing.runs.title' | translate }}
        </h2>
        <button
          (click)="showTriggerModal.set(true)"
          class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
        >
          ▶ Запустить прогон
        </button>
      </div>

      <!-- Filter Bar -->
      <dp-filter-bar
        [filters]="filterConfigs"
        [values]="filterValues()"
        (filtersChanged)="onFiltersChanged($event)"
      />

      <!-- Data Grid -->
      <div class="flex-1 px-4 py-3">
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
              : 'Прогоны ценообразования ещё не выполнялись.'"
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
        <div class="absolute inset-0 bg-black/40" (click)="showTriggerModal.set(false)"></div>
        <div
          class="relative z-10 w-full max-w-sm rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-6 shadow-[var(--shadow-md)] animate-[fadeIn_150ms_ease]"
        >
          <h3 class="text-base font-semibold text-[var(--text-primary)]">Запустить прогон</h3>
          <p class="mt-1 text-sm text-[var(--text-secondary)]">
            Выберите подключение для ручного запуска прогона ценообразования.
          </p>

          <div class="mt-4 flex flex-col gap-1.5">
            <label class="text-[11px] text-[var(--text-tertiary)]">Подключение</label>
            @if (connectionsQuery.isPending()) {
              <div class="h-9 rounded-[var(--radius-md)] bg-[var(--bg-tertiary)] dp-shimmer"></div>
            } @else {
              <select
                [(ngModel)]="selectedConnectionId"
                class="h-9 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
              >
                <option [ngValue]="null" disabled>Выберите подключение</option>
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
              Отмена
            </button>
            <button
              (click)="triggerRun()"
              [disabled]="!selectedConnectionId || triggerMutation.isPending()"
              class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              @if (triggerMutation.isPending()) {
                Запуск...
              } @else {
                Запустить
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
  private readonly queryClient = inject(QueryClient);

  readonly filterValues = signal<Record<string, any>>({});
  readonly showTriggerModal = signal(false);
  readonly currentPage = signal(0);
  readonly currentSort = signal('createdAt,desc');
  protected selectedConnectionId: number | null = null;

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: 'Статус',
      type: 'multi-select',
      options: Object.entries(RUN_STATUS_LABEL).map(([value, label]) => ({ value, label })),
    },
    {
      key: 'triggerType',
      label: 'Триггер',
      type: 'multi-select',
      options: Object.entries(TRIGGER_LABEL).map(([value, label]) => ({ value, label })),
    },
    { key: 'period', label: 'Период', type: 'date-range' },
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
      headerName: 'Триггер',
      field: 'triggerType',
      width: 130,
      sortable: true,
      cellRenderer: (params: any) => {
        const t = params.value as string;
        const label = TRIGGER_LABEL[t] ?? t;
        const color = TRIGGER_COLOR[t] ?? 'var(--text-secondary)';
        return `<span class="inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                  style="background-color: color-mix(in srgb, ${color} 12%, transparent); color: ${color}">
          ${label}
        </span>`;
      },
    },
    {
      headerName: 'Подключение',
      field: 'connectionName',
      minWidth: 160,
      flex: 1,
      sortable: true,
    },
    {
      headerName: 'Статус',
      field: 'status',
      width: 140,
      sortable: true,
      cellRenderer: (params: any) => {
        const st = params.value as string;
        const label = RUN_STATUS_LABEL[st] ?? st;
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
      headerName: 'Всего',
      field: 'totalOffers',
      width: 80,
      sortable: true,
      cellClass: 'font-mono text-right',
    },
    {
      headerName: 'Подходит',
      field: 'eligibleCount',
      width: 90,
      sortable: true,
      cellClass: 'font-mono text-right',
    },
    {
      headerName: 'Изменение',
      field: 'changeCount',
      width: 100,
      sortable: true,
      cellClass: 'font-mono text-right',
      cellStyle: () => ({ color: 'var(--status-success)' }),
    },
    {
      headerName: 'Пропуск',
      field: 'skipCount',
      width: 90,
      sortable: true,
      cellClass: 'font-mono text-right',
      cellStyle: () => ({ color: 'var(--status-warning)' }),
    },
    {
      headerName: 'Ожидание',
      field: 'holdCount',
      width: 90,
      sortable: true,
      cellClass: 'font-mono text-right',
      cellStyle: () => ({ color: 'var(--text-tertiary)' }),
    },
    {
      headerName: 'Длительность',
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
      headerName: 'Создан',
      field: 'createdAt',
      width: 120,
      sortable: true,
      sort: 'desc',
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
      this.toast.success('Прогон ценообразования запущен');
    },
    onError: () => {
      this.toast.error('Не удалось запустить прогон. Попробуйте позже.');
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
    if (totalSec < 60) return `${totalSec} сек`;
    const min = Math.floor(totalSec / 60);
    const sec = totalSec % 60;
    return sec > 0 ? `${min} мин ${sec} сек` : `${min} мин`;
  }

  private formatRelativeTime(iso: string | null): string {
    if (!iso) return '—';
    const diff = Date.now() - new Date(iso).getTime();
    const minutes = Math.floor(diff / 60_000);
    if (minutes < 1) return 'только что';
    if (minutes < 60) return `${minutes} мин назад`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours} ч назад`;
    const days = Math.floor(hours / 24);
    if (days === 1) return 'вчера';
    return `${days} дн назад`;
  }
}
