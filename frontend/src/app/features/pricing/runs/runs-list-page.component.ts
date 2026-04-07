import {
  ChangeDetectionStrategy,
  Component,
  computed,
  HostListener,
  inject,
  signal,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PricingApiService } from '@core/api/pricing-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { ConnectionApiService } from '@core/api/connection-api.service';
import {
  PricingRunSummary,
  PricingRunFilter,
  ConnectionSummary,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import {
  FilterBarComponent,
  FilterConfig,
} from '@shared/components/filter-bar/filter-bar.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { formatRelativeTime, formatMoney } from '@shared/utils/format.utils';

interface ColumnDef {
  key: string;
  label: string;
  sortField?: string;
  defaultWidth: number;
  minWidth: number;
  align: 'left' | 'right';
  flex?: boolean;
}

const RUN_STATUS_COLOR: Record<string, string> = {
  PENDING: 'var(--status-info)',
  IN_PROGRESS: 'var(--status-info)',
  COMPLETED: 'var(--status-success)',
  COMPLETED_WITH_ERRORS: 'var(--status-warning)',
  FAILED: 'var(--status-error)',
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
    FormsModule,
    RouterLink,
    NgTemplateOutlet,
    FilterBarComponent,
    EmptyStateComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  templateUrl: './runs-list-page.component.html',
  styles: [`
    .run-row {
      border-left: 3px solid transparent;
      transition: background-color 150ms ease, border-color 150ms ease;
    }
    .run-row:hover { background-color: var(--bg-secondary); }
    .run-row.expanded {
      background-color: var(--bg-secondary);
      border-bottom-color: transparent;
    }
    .peek-section {
      animation: peekReveal 200ms ease-out;
    }
    @keyframes peekReveal {
      from { opacity: 0; transform: translateY(-4px); }
      to { opacity: 1; transform: translateY(0); }
    }
    @keyframes fadeIn {
      from { opacity: 0; transform: scale(0.97); }
      to { opacity: 1; transform: scale(1); }
    }
    .resize-handle {
      position: absolute;
      right: 0;
      top: 4px;
      bottom: 4px;
      width: 3px;
      cursor: col-resize;
      border-radius: 1px;
      opacity: 0;
      background-color: var(--accent-primary);
      transition: opacity 120ms ease;
    }
    th:hover .resize-handle,
    .resize-handle:active {
      opacity: 1;
    }
  `],
})
export class RunsListPageComponent {
  private readonly pricingApi = inject(PricingApiService);
  private readonly connectionApi = inject(ConnectionApiService);
  protected readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);
  protected readonly rbac = inject(RbacService);

  readonly columnDefs: ColumnDef[] = [
    { key: 'id', label: '#', sortField: 'id', defaultWidth: 50, minWidth: 40, align: 'right' },
    { key: 'trigger', label: this.translate.instant('pricing.runs.col.trigger'), sortField: 'triggerType', defaultWidth: 110, minWidth: 80, align: 'left' },
    { key: 'connection', label: this.translate.instant('pricing.runs.col.connection'), sortField: 'connectionName', defaultWidth: 180, minWidth: 100, align: 'left', flex: true },
    { key: 'status', label: this.translate.instant('pricing.runs.col.status'), sortField: 'status', defaultWidth: 130, minWidth: 90, align: 'left' },
    { key: 'total', label: this.translate.instant('pricing.runs.col.total'), sortField: 'totalOffers', defaultWidth: 60, minWidth: 45, align: 'right' },
    { key: 'change', label: this.translate.instant('pricing.runs.col.change'), sortField: 'changeCount', defaultWidth: 65, minWidth: 45, align: 'right' },
    { key: 'skip', label: this.translate.instant('pricing.runs.col.skip'), sortField: 'skipCount', defaultWidth: 65, minWidth: 45, align: 'right' },
    { key: 'duration', label: this.translate.instant('pricing.runs.col.duration'), defaultWidth: 90, minWidth: 60, align: 'right' },
    { key: 'created', label: this.translate.instant('pricing.runs.col.created_at'), sortField: 'createdAt', defaultWidth: 100, minWidth: 70, align: 'right' },
    { key: 'expand', label: '', defaultWidth: 32, minWidth: 32, align: 'left' },
  ];

  readonly columnWidths = signal<Record<string, number>>(
    Object.fromEntries(
      this.columnDefs.map((c) => [c.key, c.defaultWidth]),
    ),
  );

  readonly currentSort = signal<{
    field: string;
    dir: 'asc' | 'desc';
  }>({ field: 'createdAt', dir: 'desc' });

  readonly sortParam = computed(() => {
    const s = this.currentSort();
    return `${s.field},${s.dir}`;
  });

  readonly filterValues = signal<Record<string, any>>({});
  readonly showTriggerModal = signal(false);
  readonly expandedRunId = signal<number | null>(null);
  protected selectedConnectionId: number | null = null;

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: 'pricing.runs.filter.status',
      type: 'multi-select',
      options: (
        [
          'PENDING',
          'IN_PROGRESS',
          'COMPLETED',
          'COMPLETED_WITH_ERRORS',
          'FAILED',
        ] as const
      ).map((value) => ({
        value,
        label: `pricing.runs.status.${value}`,
      })),
    },
    {
      key: 'triggerType',
      label: 'pricing.runs.filter.trigger',
      type: 'multi-select',
      options: (
        [
          'POST_SYNC',
          'MANUAL',
          'SCHEDULED',
          'POLICY_CHANGE',
        ] as const
      ).map((value) => ({
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

  private readonly filter = computed<PricingRunFilter>(() => {
    const vals = this.filterValues();
    const f: PricingRunFilter = {};
    if (vals['status']?.length) f.status = vals['status'];
    if (vals['triggerType']?.length)
      f.triggerType = vals['triggerType'];
    if (vals['period']?.from) f.from = vals['period'].from;
    if (vals['period']?.to) f.to = vals['period'].to;
    return f;
  });

  readonly runsQuery = injectQuery(() => ({
    queryKey: [
      'pricing-runs',
      this.wsStore.currentWorkspaceId(),
      this.filter(),
      this.sortParam(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.listRuns(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          0,
          50,
          this.sortParam(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections'],
    queryFn: () =>
      lastValueFrom(this.connectionApi.listConnections()),
    enabled: this.showTriggerModal(),
    staleTime: 60_000,
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

  readonly rows = computed(
    () => this.runsQuery.data()?.content ?? [],
  );

  readonly activeConnections = computed<ConnectionSummary[]>(() =>
    (this.connectionsQuery.data() ?? []).filter(
      (c) => c.status === 'ACTIVE',
    ),
  );

  readonly hasActiveFilters = computed(() =>
    Object.values(this.filterValues()).some(
      (v) =>
        v !== '' &&
        v !== null &&
        v !== undefined &&
        (!Array.isArray(v) || v.length > 0),
    ),
  );

  readonly peekDecisions = computed(
    () => this.peekDecisionsQuery.data()?.content ?? [],
  );

  readonly peekDecisionsTotalCount = computed(
    () => this.peekDecisionsQuery.data()?.totalElements ?? 0,
  );

  readonly triggerMutation = injectMutation(() => ({
    mutationFn: (connectionId: number) =>
      lastValueFrom(
        this.pricingApi.triggerManualRun(
          this.wsStore.currentWorkspaceId()!,
          connectionId,
        ),
      ),
    onSuccess: () => {
      this.showTriggerModal.set(false);
      this.selectedConnectionId = null;
      this.queryClient.invalidateQueries({
        queryKey: ['pricing-runs'],
      });
      this.toast.success(
        this.translate.instant('pricing.runs.trigger_success'),
      );
    },
    onError: () => {
      this.toast.error(
        this.translate.instant('pricing.runs.trigger_error'),
      );
    },
  }));

  private resizingCol: string | null = null;
  private resizeStartX = 0;
  private resizeStartWidth = 0;

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.expandedRunId() !== null) {
      this.closeExpand();
    }
  }

  colWidth(key: string): number {
    return this.columnWidths()[key] ?? 80;
  }

  sortDir(colKey: string): 'asc' | 'desc' | null {
    const col = this.columnDefs.find((c) => c.key === colKey);
    if (!col?.sortField) return null;
    const s = this.currentSort();
    return s.field === col.sortField ? s.dir : null;
  }

  toggleSort(colKey: string): void {
    const col = this.columnDefs.find((c) => c.key === colKey);
    if (!col?.sortField) return;

    const current = this.currentSort();
    if (current.field === col.sortField) {
      this.currentSort.set({
        field: col.sortField,
        dir: current.dir === 'asc' ? 'desc' : 'asc',
      });
    } else {
      this.currentSort.set({ field: col.sortField, dir: 'asc' });
    }
    this.expandedRunId.set(null);
  }

  startResize(event: MouseEvent, colKey: string): void {
    event.stopPropagation();
    event.preventDefault();
    this.resizingCol = colKey;
    this.resizeStartX = event.clientX;
    this.resizeStartWidth = this.colWidth(colKey);

    const col = this.columnDefs.find((c) => c.key === colKey);
    const min = col?.minWidth ?? 40;

    const onMouseMove = (e: MouseEvent) => {
      if (!this.resizingCol) return;
      const delta = e.clientX - this.resizeStartX;
      const newWidth = Math.max(min, this.resizeStartWidth + delta);
      this.columnWidths.update((w) => ({
        ...w,
        [this.resizingCol!]: newWidth,
      }));
    };

    const onMouseUp = () => {
      this.resizingCol = null;
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  }

  toggleExpand(run: PricingRunSummary): void {
    this.expandedRunId.set(
      this.expandedRunId() === run.id ? null : run.id,
    );
  }

  closeExpand(): void {
    this.expandedRunId.set(null);
  }

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
    this.expandedRunId.set(null);
  }

  triggerRun(): void {
    if (this.selectedConnectionId) {
      this.triggerMutation.mutate(this.selectedConnectionId);
    }
  }

  statusColor(status: string): string {
    return RUN_STATUS_COLOR[status] ?? 'var(--text-tertiary)';
  }

  statusBg(status: string): string {
    return `color-mix(in srgb, ${this.statusColor(status)} 12%, transparent)`;
  }

  triggerColor(trigger: string): string {
    return TRIGGER_COLOR[trigger] ?? 'var(--text-secondary)';
  }

  triggerBg(trigger: string): string {
    return `color-mix(in srgb, ${this.triggerColor(trigger)} 12%, transparent)`;
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
    if (value === null || value === undefined)
      return 'var(--text-tertiary)';
    if (value > 0) return 'var(--finance-positive)';
    if (value < 0) return 'var(--finance-negative)';
    return 'var(--finance-zero)';
  }

  formatDuration(
    start: string | null,
    end: string | null,
  ): string {
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

  relativeTime(iso: string | null): string {
    return formatRelativeTime(iso);
  }

  formatPrice(value: number | null): string {
    return formatMoney(value, 0);
  }
}
