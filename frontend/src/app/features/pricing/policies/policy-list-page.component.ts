import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PricingApiService } from '@core/api/pricing-api.service';
import { formatRelativeTime } from '@shared/utils/format.utils';
import { PricingFilter, PricingPolicySummary } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';

const POLICY_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'neutral',
  ACTIVE: 'success',
  PAUSED: 'warning',
  ARCHIVED: 'neutral',
};

const POLICY_STATUS_LABEL: Record<string, string> = {
  DRAFT: 'Черновик',
  ACTIVE: 'Активна',
  PAUSED: 'Приостановлена',
  ARCHIVED: 'В архиве',
};

const EXECUTION_MODE_COLOR: Record<string, string> = {
  RECOMMENDATION: 'info',
  SEMI_AUTO: 'warning',
  FULL_AUTO: 'success',
  SIMULATED: 'neutral',
};

const EXECUTION_MODE_LABEL: Record<string, string> = {
  RECOMMENDATION: 'Рекомендация',
  SEMI_AUTO: 'Полуавто',
  FULL_AUTO: 'Автомат',
  SIMULATED: 'Симуляция',
};

const STRATEGY_TYPE_LABEL: Record<string, string> = {
  TARGET_MARGIN: 'Целевая маржа',
  PRICE_CORRIDOR: 'Ценовой коридор',
};

@Component({
  selector: 'dp-policy-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    FilterBarComponent,
    DataGridComponent,
    EmptyStateComponent,
    ConfirmationModalComponent,
  ],
  template: `
    <div class="flex h-full flex-col">
      <!-- Toolbar -->
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-3">
        <h2 class="text-base font-semibold text-[var(--text-primary)]">
          {{ 'pricing.policies.title' | translate }}
        </h2>
        <button
          (click)="navigateToCreate()"
          class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
        >
          {{ 'pricing.policies.create' | translate }}
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
        @if (policiesQuery.isError()) {
          <dp-empty-state
            [message]="'pricing.policies.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="policiesQuery.refetch()"
          />
        } @else if (!policiesQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="hasActiveFilters()
              ? ('pricing.policies.empty_filtered' | translate)
              : ('pricing.policies.empty' | translate)"
            [actionLabel]="hasActiveFilters()
              ? ('filter_bar.reset_all' | translate)
              : ('pricing.policies.create' | translate)"
            (action)="hasActiveFilters() ? onFiltersChanged({}) : navigateToCreate()"
          />
        } @else {
          <dp-data-grid
            [columnDefs]="columnDefs"
            [rowData]="rows()"
            [loading]="policiesQuery.isPending()"
            [pagination]="true"
            [pageSize]="50"
            [getRowId]="getRowId"
            [height]="'100%'"
            (rowClicked)="onRowClicked($event)"
          />
        }
      </div>
    </div>

    <!-- Activate Confirmation Modal -->
    <dp-confirmation-modal
      [open]="showActivateModal()"
      [title]="'pricing.policies.activate_title' | translate"
      [message]="activateMessage()"
      [confirmLabel]="'pricing.policies.activate_confirm' | translate"
      (confirmed)="executeActivate()"
      (cancelled)="showActivateModal.set(false)"
    />

    <!-- Pause Confirmation Modal -->
    <dp-confirmation-modal
      [open]="showPauseModal()"
      [title]="'pricing.policies.pause_title' | translate"
      [message]="pauseMessage()"
      [confirmLabel]="'pricing.policies.pause_confirm' | translate"
      (confirmed)="executePause()"
      (cancelled)="showPauseModal.set(false)"
    />

    <!-- Archive Confirmation Modal -->
    <dp-confirmation-modal
      [open]="showArchiveModal()"
      [title]="'pricing.policies.archive_title' | translate"
      [message]="archiveMessage()"
      [confirmLabel]="'pricing.policies.archive_confirm' | translate"
      [danger]="true"
      (confirmed)="executeArchive()"
      (cancelled)="showArchiveModal.set(false)"
    />
  `,
})
export class PolicyListPageComponent {
  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);

  readonly filterValues = signal<Record<string, any>>({
    status: ['DRAFT', 'ACTIVE', 'PAUSED'],
  });
  readonly currentPage = signal(0);
  readonly currentSort = signal('createdAt,desc');

  readonly showActivateModal = signal(false);
  readonly activateTarget = signal<PricingPolicySummary | null>(null);
  readonly showPauseModal = signal(false);
  readonly pauseTarget = signal<PricingPolicySummary | null>(null);
  readonly showArchiveModal = signal(false);
  readonly archiveTarget = signal<PricingPolicySummary | null>(null);

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: 'Статус',
      type: 'multi-select',
      options: Object.entries(POLICY_STATUS_LABEL).map(([value, label]) => ({
        value,
        label,
      })),
    },
    {
      key: 'strategyType',
      label: 'Стратегия',
      type: 'select',
      options: Object.entries(STRATEGY_TYPE_LABEL).map(([value, label]) => ({
        value,
        label,
      })),
    },
    {
      key: 'executionMode',
      label: 'Режим',
      type: 'multi-select',
      options: Object.entries(EXECUTION_MODE_LABEL).map(([value, label]) => ({
        value,
        label,
      })),
    },
  ];

  readonly columnDefs = [
    {
      headerName: 'Название',
      field: 'name',
      minWidth: 250,
      pinned: 'left' as const,
      sortable: true,
      cellRenderer: (params: any) => {
        if (!params.data) return '';
        return `<span class="font-medium text-[var(--accent-primary)] cursor-pointer hover:underline">${params.data.name}</span>`;
      },
    },
    {
      headerName: 'Стратегия',
      field: 'strategyType',
      width: 160,
      sortable: true,
      cellRenderer: (params: any) => {
        const val = params.value as string;
        const label = STRATEGY_TYPE_LABEL[val] ?? val;
        return `<span class="inline-flex items-center rounded-full bg-[var(--bg-tertiary)] px-2.5 py-0.5 text-[11px] font-medium text-[var(--text-secondary)]">${label}</span>`;
      },
    },
    {
      headerName: 'Режим',
      field: 'executionMode',
      width: 130,
      sortable: true,
      cellRenderer: (params: any) => {
        const val = params.value as string;
        const label = EXECUTION_MODE_LABEL[val] ?? val;
        const color = EXECUTION_MODE_COLOR[val] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        return `<span class="inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                  style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}">
          ${label}
        </span>`;
      },
    },
    {
      headerName: 'Статус',
      field: 'status',
      width: 150,
      sortable: true,
      cellRenderer: (params: any) => {
        const st = params.value as string;
        const label = POLICY_STATUS_LABEL[st] ?? st;
        const color = POLICY_STATUS_COLOR[st] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        return `<span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                  style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}">
          <span class="inline-block h-1.5 w-1.5 rounded-full" style="background-color: ${cssVar}"></span>
          ${label}
        </span>`;
      },
    },
    {
      headerName: 'Приоритет',
      field: 'priority',
      width: 90,
      sortable: true,
      cellClass: 'font-mono text-center',
    },
    {
      headerName: 'Версия',
      field: 'version',
      width: 80,
      sortable: true,
      cellClass: 'font-mono text-center',
      valueFormatter: (params: any) =>
        params.value != null ? `v${params.value}` : '',
    },
    {
      headerName: 'Назначений',
      field: 'assignmentsCount',
      width: 110,
      sortable: true,
      cellClass: 'font-mono text-center',
    },
    {
      headerName: 'Создана',
      field: 'createdAt',
      width: 120,
      sortable: true,
      sort: 'desc' as const,
      valueFormatter: (params: any) => this.formatRelativeTime(params.value),
    },
    {
      headerName: 'Обновлена',
      field: 'updatedAt',
      width: 120,
      sortable: true,
      valueFormatter: (params: any) => this.formatRelativeTime(params.value),
    },
    {
      headerName: '',
      field: 'actions',
      width: 130,
      sortable: false,
      suppressMovable: true,
      cellRenderer: (params: any) => {
        if (!params.data) return '';
        const policy = params.data as PricingPolicySummary;
        let buttons = `<button class="action-btn" data-action="edit" title="Редактировать">✏</button>`;
        if (policy.status === 'DRAFT' || policy.status === 'PAUSED') {
          buttons += `<button class="action-btn" data-action="activate" title="Активировать">▶</button>`;
        }
        if (policy.status === 'ACTIVE') {
          buttons += `<button class="action-btn" data-action="pause" title="Приостановить">⏸</button>`;
        }
        if (policy.status !== 'ARCHIVED') {
          buttons += `<button class="action-btn" data-action="archive" title="Архивировать">📦</button>`;
        }
        return `<div class="flex items-center gap-1">${buttons}</div>`;
      },
      onCellClicked: (params: any) => {
        const target = params.event?.target as HTMLElement;
        const action = target?.closest('[data-action]')?.getAttribute('data-action');
        if (!action || !params.data) return;
        const policy = params.data as PricingPolicySummary;
        switch (action) {
          case 'edit':
            this.navigateToEdit(policy.id);
            break;
          case 'activate':
            this.activateTarget.set(policy);
            this.showActivateModal.set(true);
            break;
          case 'pause':
            this.pauseTarget.set(policy);
            this.showPauseModal.set(true);
            break;
          case 'archive':
            this.archiveTarget.set(policy);
            this.showArchiveModal.set(true);
            break;
        }
      },
    },
  ];

  private readonly filter = computed<PricingFilter>(() => {
    const vals = this.filterValues();
    const f: PricingFilter = {};
    if (vals['status']?.length) f.status = vals['status'];
    if (vals['strategyType']) f.strategyType = vals['strategyType'];
    if (vals['executionMode']?.length) f.executionMode = vals['executionMode'];
    return f;
  });

  readonly policiesQuery = injectQuery(() => ({
    queryKey: [
      'policies',
      this.wsStore.currentWorkspaceId(),
      this.filter(),
      this.currentPage(),
      this.currentSort(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.listPolicies(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.currentPage(),
          50,
          this.currentSort(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.policiesQuery.data()?.content ?? []);

  readonly hasActiveFilters = computed(() =>
    Object.values(this.filterValues()).some(
      (v) =>
        v !== '' &&
        v !== null &&
        v !== undefined &&
        (!Array.isArray(v) || v.length > 0),
    ),
  );

  readonly activateMessage = computed(() => {
    const p = this.activateTarget();
    return p ? `Активировать политику «${p.name}»?` : '';
  });

  readonly pauseMessage = computed(() => {
    const p = this.pauseTarget();
    return p ? `Приостановить политику «${p.name}»?` : '';
  });

  readonly archiveMessage = computed(() => {
    const p = this.archiveTarget();
    return p
      ? `Архивировать политику «${p.name}»? Это действие нельзя отменить.`
      : '';
  });

  private readonly activateMutation = injectMutation(() => ({
    mutationFn: (policyId: number) =>
      lastValueFrom(
        this.pricingApi.activatePolicy(
          this.wsStore.currentWorkspaceId()!,
          policyId,
        ),
      ),
    onSuccess: () => {
      this.showActivateModal.set(false);
      this.activateTarget.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['policies'] });
      this.toast.success('Политика активирована');
    },
    onError: () => {
      this.showActivateModal.set(false);
      this.toast.error('Не удалось активировать политику');
    },
  }));

  private readonly pauseMutation = injectMutation(() => ({
    mutationFn: (policyId: number) =>
      lastValueFrom(
        this.pricingApi.pausePolicy(
          this.wsStore.currentWorkspaceId()!,
          policyId,
        ),
      ),
    onSuccess: () => {
      this.showPauseModal.set(false);
      this.pauseTarget.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['policies'] });
      this.toast.success('Политика приостановлена');
    },
    onError: () => {
      this.showPauseModal.set(false);
      this.toast.error('Не удалось приостановить политику');
    },
  }));

  private readonly archiveMutation = injectMutation(() => ({
    mutationFn: (policyId: number) =>
      lastValueFrom(
        this.pricingApi.archivePolicy(
          this.wsStore.currentWorkspaceId()!,
          policyId,
        ),
      ),
    onSuccess: () => {
      this.showArchiveModal.set(false);
      this.archiveTarget.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['policies'] });
      this.toast.success('Политика архивирована');
    },
    onError: () => {
      this.showArchiveModal.set(false);
      this.toast.error('Не удалось архивировать политику');
    },
  }));

  readonly getRowId = (params: any) => String(params.data.id);

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
    this.currentPage.set(0);
  }

  onRowClicked(row: PricingPolicySummary): void {
    this.navigateToEdit(row.id);
  }

  navigateToCreate(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'pricing', 'policies', 'new']);
  }

  executeActivate(): void {
    const target = this.activateTarget();
    if (target) {
      this.activateMutation.mutate(target.id);
    }
  }

  executePause(): void {
    const target = this.pauseTarget();
    if (target) {
      this.pauseMutation.mutate(target.id);
    }
  }

  executeArchive(): void {
    const target = this.archiveTarget();
    if (target) {
      this.archiveMutation.mutate(target.id);
    }
  }

  private navigateToEdit(policyId: number): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate([
      '/workspace', wsId, 'pricing', 'policies', policyId, 'edit',
    ]);
  }

  private formatRelativeTime(iso: string | null): string {
    return formatRelativeTime(iso);
  }
}
