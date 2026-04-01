import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
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

const DECISION_COLOR: Record<string, string> = {
  CHANGE: 'success',
  SKIP: 'warning',
  HOLD: 'neutral',
};

const DECISION_LABEL: Record<string, string> = {
  CHANGE: 'Изменение',
  SKIP: 'Пропуск',
  HOLD: 'Ожидание',
};

const STRATEGY_LABEL: Record<string, string> = {
  TARGET_MARGIN: 'Целевая маржа',
  PRICE_CORRIDOR: 'Ценовой коридор',
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
  template: `
    <div class="flex h-full flex-col">
      <!-- Back button -->
      <div class="flex items-center gap-3 border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2.5">
        <button
          (click)="goBack()"
          class="cursor-pointer rounded-[var(--radius-sm)] px-2 py-1 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
        >
          ← Назад к прогонам
        </button>
        @if (run()) {
          <span class="text-sm font-medium text-[var(--text-primary)]">
            Прогон #{{ run()!.id }}
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
        <div class="flex gap-3 bg-[var(--bg-secondary)] px-4 py-3">
          <dp-kpi-card
            label="Всего офферов"
            [value]="run()!.totalOffers"
            [loading]="false"
          />
          <dp-kpi-card
            label="Подходящих"
            [value]="run()!.eligibleCount"
            [loading]="false"
          />
          <dp-kpi-card
            label="Изменений"
            [value]="run()!.changeCount"
            [loading]="false"
          />
          <dp-kpi-card
            label="Пропусков"
            [value]="run()!.skipCount"
            [loading]="false"
          />
          <dp-kpi-card
            label="Ожидание"
            [value]="run()!.holdCount"
            [loading]="false"
          />
        </div>

        <!-- Meta info -->
        <div class="flex flex-wrap items-center gap-3 border-b border-[var(--border-default)] px-4 py-2.5">
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
          <div class="mx-4 mt-3 rounded-[var(--radius-md)] border border-[var(--status-error)] bg-[color-mix(in_srgb,var(--status-error)_8%,transparent)] px-4 py-2.5 text-sm text-[var(--status-error)]">
            {{ run()!.errorDetails }}
          </div>
        }

        <!-- Decision filter -->
        <dp-filter-bar
          [filters]="decisionFilterConfigs"
          [values]="decisionFilterValues()"
          (filtersChanged)="onDecisionFiltersChanged($event)"
        />

        <!-- Decision Grid -->
        <div class="flex-1 px-4 py-3">
          @if (decisionsQuery.isError()) {
            <dp-empty-state
              [message]="'pricing.decisions.error' | translate"
              [actionLabel]="'actions.retry' | translate"
              (action)="decisionsQuery.refetch()"
            />
          } @else if (!decisionsQuery.isPending() && decisionRows().length === 0) {
            <dp-empty-state
              message="Решений по данному прогону пока нет."
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
  readonly statusLabel = computed(() => RUN_STATUS_LABEL[this.run()?.status ?? ''] ?? '');
  readonly triggerLabel = computed(() => TRIGGER_LABEL[this.run()?.triggerType ?? ''] ?? '');

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
    if (r.status === 'IN_PROGRESS') return `Начат ${started}`;
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
      label: 'Тип решения',
      type: 'multi-select',
      options: Object.entries(DECISION_LABEL).map(([value, label]) => ({ value, label })),
    },
  ];

  readonly decisionColumnDefs = [
    {
      headerName: 'Оффер',
      field: 'offerName',
      minWidth: 200,
      flex: 1,
      sortable: true,
    },
    {
      headerName: 'Артикул',
      field: 'sellerSku',
      width: 120,
      sortable: true,
      cellClass: 'font-mono text-[length:var(--text-sm)]',
    },
    {
      headerName: 'Решение',
      field: 'decisionType',
      width: 120,
      sortable: true,
      cellRenderer: (params: any) => {
        const dt = params.value as string;
        const label = DECISION_LABEL[dt] ?? dt;
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
      headerName: 'Текущая цена',
      field: 'currentPrice',
      width: 120,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: (params: any) => this.formatPrice(params.value),
    },
    {
      headerName: 'Целевая цена',
      field: 'targetPrice',
      width: 120,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: (params: any) => this.formatPrice(params.value),
    },
    {
      headerName: 'Δ цены',
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
      headerName: 'Политика',
      field: 'policyName',
      width: 160,
      sortable: true,
    },
    {
      headerName: 'Стратегия',
      field: 'strategyType',
      width: 140,
      sortable: true,
      cellRenderer: (params: any) => {
        const st = params.value as string;
        const label = STRATEGY_LABEL[st] ?? st;
        return `<span class="inline-flex items-center rounded-full border border-[var(--border-default)] px-2 py-0.5 text-[11px] text-[var(--text-secondary)]">
          ${label}
        </span>`;
      },
    },
    {
      headerName: 'Причина пропуска',
      field: 'skipReason',
      width: 200,
      sortable: false,
      cellClass: 'text-[length:var(--text-sm)] text-[color:var(--text-tertiary)]',
      valueFormatter: (params: any) => params.value ?? '—',
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
    if (totalSec < 60) return `${totalSec} сек`;
    const min = Math.floor(totalSec / 60);
    const sec = totalSec % 60;
    return sec > 0 ? `${min} мин ${sec} сек` : `${min} мин`;
  }
}
