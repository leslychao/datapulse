import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { Router } from '@angular/router';
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
import { PricingFilter, PricingPolicySummary } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { GuidedTourService } from '@shared/services/guided-tour.service';
import { TourProgressStore } from '@shared/stores/tour-progress.store';
import { PRICING_POLICIES_TOUR } from '../tours/pricing-tours';
import {
  FilterBarComponent,
  FilterConfig,
} from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { formatRelativeTime, renderBadge } from '@shared/utils/format.utils';
import { FilterBarUrlDef } from '@shared/utils/url-filters';
import { createListPageState } from '@shared/utils/list-page-state';

const POLICY_STATUSES = ['DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED'] as const;
const EXECUTION_MODES = [
  'RECOMMENDATION', 'SEMI_AUTO', 'FULL_AUTO', 'SIMULATED',
] as const;
const STRATEGY_TYPES = [
  'TARGET_MARGIN', 'PRICE_CORRIDOR', 'VELOCITY_ADAPTIVE',
  'STOCK_BALANCING', 'COMPOSITE', 'COMPETITOR_ANCHOR',
] as const;

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'info',
  ACTIVE: 'success',
  PAUSED: 'warning',
  ARCHIVED: 'neutral',
};

const MODE_COLOR: Record<string, string> = {
  RECOMMENDATION: 'info',
  SEMI_AUTO: 'warning',
  FULL_AUTO: 'success',
  SIMULATED: 'neutral',
};

@Component({
  selector: 'dp-policy-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    LowerCasePipe,
    TranslatePipe,
    FilterBarComponent,
    DataGridComponent,
    EmptyStateComponent,
    ConfirmationModalComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <div class="flex h-full flex-col">
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <div class="flex items-center gap-4">
          <h2 class="text-sm font-semibold text-[var(--text-primary)]">
            {{ 'pricing.policies.title' | translate }}
          </h2>
          @if (statusSummary().length) {
            <div class="flex items-center gap-3">
              @for (s of statusSummary(); track s.status) {
                <span class="flex items-center gap-1.5 text-[11px] text-[var(--text-tertiary)]">
                  <span class="inline-block h-1.5 w-1.5 rounded-full" [style.background-color]="s.color"></span>
                  {{ s.count }} {{ s.label | lowercase }}
                </span>
              }
            </div>
          }
        </div>
        @if (rbac.canWritePolicies()) {
          <button
            data-tour="policy-create-btn"
            (click)="navigateToCreate()"
            class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
          >
            {{ 'pricing.policies.create' | translate }}
          </button>
        }
      </div>

      <div data-tour="policy-filter-bar" class="border-b border-[var(--border-default)] px-4 py-2">
        <dp-filter-bar
          [filters]="filterConfigs"
          [values]="listState.filterValues()"
          (filtersChanged)="listState.onFiltersChanged($event)"
        />
      </div>

      <div data-tour="policy-table" class="flex-1 px-4 py-2">
        @if (policiesQuery.isError()) {
          <dp-empty-state
            [message]="'pricing.policies.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="policiesQuery.refetch()"
          />
        } @else if (!policiesQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="listState.hasActiveFilters()
              ? ('pricing.policies.empty_filtered' | translate)
              : ('pricing.policies.empty' | translate)"
            [actionLabel]="listState.hasActiveFilters()
              ? ('filter_bar.reset_all' | translate)
              : (rbac.canWritePolicies()
                ? ('pricing.policies.create' | translate)
                : '')"
            (action)="listState.hasActiveFilters()
              ? listState.resetFilters()
              : navigateToCreate()"
          />
        } @else {
          <dp-data-grid
            viewStateKey="pricing:policies"
            [columnDefs]="columnDefs()"
            [rowData]="rows()"
            [loading]="policiesQuery.isPending()"
            [pagination]="true"
            [pageSize]="50"
            [getRowId]="getRowId"
            [height]="'100%'"
            [initialSortModel]="listState.initialSortModel()"
            (sortChanged)="listState.onSortChanged($event)"
          />
        }
      </div>
    </div>

    <dp-confirmation-modal
      [open]="showActivateModal()"
      [title]="'pricing.policies.activate_title' | translate"
      [message]="activateMessage()"
      [confirmLabel]="'pricing.policies.activate_confirm' | translate"
      [cancelLabel]="'common.cancel' | translate"
      (confirmed)="executeActivate()"
      (cancelled)="showActivateModal.set(false)"
    />
    <dp-confirmation-modal
      [open]="showPauseModal()"
      [title]="'pricing.policies.pause_title' | translate"
      [message]="pauseMessage()"
      [confirmLabel]="'pricing.policies.pause_confirm' | translate"
      [cancelLabel]="'common.cancel' | translate"
      (confirmed)="executePause()"
      (cancelled)="showPauseModal.set(false)"
    />
    <dp-confirmation-modal
      [open]="showArchiveModal()"
      [title]="'pricing.policies.archive_title' | translate"
      [message]="archiveMessage()"
      [confirmLabel]="'pricing.policies.archive_confirm' | translate"
      [cancelLabel]="'common.cancel' | translate"
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
  private readonly translate = inject(TranslateService);
  private readonly tourService = inject(GuidedTourService);
  private readonly tourProgress = inject(TourProgressStore);
  protected readonly rbac = inject(RbacService);

  readonly listState = createListPageState({
    pageKey: 'pricing:policies',
    defaultSort: { column: '', direction: 'desc' },
    filterBarDefs: [
      { key: 'status', type: 'csv' },
      { key: 'strategyType', type: 'string' },
      { key: 'executionMode', type: 'csv' },
    ] satisfies FilterBarUrlDef[],
  });

  constructor() {
    if (
      !Object.values(this.listState.filterValues()).some(
        (v) =>
          v !== '' &&
          v !== null &&
          v !== undefined &&
          (!Array.isArray(v) || v.length > 0),
      )
    ) {
      this.listState.filterValues.set({ status: ['DRAFT', 'ACTIVE', 'PAUSED'] });
    }

    if (PRICING_POLICIES_TOUR.triggerOnFirstVisit && !this.tourProgress.isCompleted(PRICING_POLICIES_TOUR.id)) {
      this.tourService.startWhenReady(PRICING_POLICIES_TOUR);
    }
  }

  readonly showActivateModal = signal(false);
  readonly activateTarget = signal<PricingPolicySummary | null>(null);
  readonly showPauseModal = signal(false);
  readonly pauseTarget = signal<PricingPolicySummary | null>(null);
  readonly showArchiveModal = signal(false);
  readonly archiveTarget = signal<PricingPolicySummary | null>(null);

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: this.translate.instant('pricing.policies.filter.status'),
      type: 'multi-select',
      options: POLICY_STATUSES.map((value) => ({
        value,
        label: this.translate.instant('pricing.policies.status.' + value),
      })),
    },
    {
      key: 'strategyType',
      label: this.translate.instant('pricing.policies.filter.strategy_type'),
      type: 'select',
      options: STRATEGY_TYPES.map((value) => ({
        value,
        label: this.translate.instant('pricing.policies.strategy.' + value),
      })),
    },
    {
      key: 'executionMode',
      label: this.translate.instant('pricing.policies.filter.execution_mode'),
      type: 'multi-select',
      options: EXECUTION_MODES.map((value) => ({
        value,
        label: this.translate.instant('pricing.policies.mode.' + value),
      })),
    },
  ];

  private readonly translationChange = toSignal(
    this.translate.onTranslationChange.pipe(startWith(null)),
  );

  readonly columnDefs = computed(() => {
    this.translationChange();
    return [
      {
        headerName: this.translate.instant('pricing.policies.col.name'),
        field: 'name',
        minWidth: 180,
        sortable: true,
        cellRenderer: (params: any) => {
          if (!params.data) return '';
          return `<span class="font-medium text-[var(--accent-primary)] cursor-pointer hover:underline" title="${params.data.name}">${params.data.name}</span>`;
        },
        onCellClicked: (params: any) => {
          if (params.data) this.onRowClicked(params.data);
        },
      },
      {
        headerName: this.translate.instant('pricing.policies.col.status'),
        headerTooltip: this.translate.instant('pricing.policies.col.status'),
        field: 'status',
        width: 130,
        sortable: true,
        cellRenderer: (params: any) => {
          const st = params.value as string;
          const label = this.translate.instant('pricing.policies.status.' + st);
          return renderBadge(label, `var(--status-${STATUS_COLOR[st] ?? 'neutral'})`, st === 'ACTIVE');
        },
      },
      {
        headerName: this.translate.instant('pricing.policies.col.strategy_type'),
        field: 'strategyType',
        width: 170,
        sortable: true,
        valueFormatter: (params: any) =>
          this.translate.instant('pricing.policies.strategy.' + params.value),
      },
      {
        headerName: this.translate.instant('pricing.policies.col.execution_mode'),
        field: 'executionMode',
        width: 150,
        sortable: true,
        cellRenderer: (params: any) => {
          const mode = params.value as string;
          const label = this.translate.instant('pricing.policies.mode.' + mode);
          return renderBadge(label, `var(--status-${MODE_COLOR[mode] ?? 'neutral'})`);
        },
      },
      {
        headerName: this.translate.instant('pricing.policies.col.priority'),
        headerTooltip: this.translate.instant('pricing.policies.col.priority'),
        field: 'priority',
        width: 100,
        sortable: true,
        cellClass: 'font-mono text-right',
      },
      {
        headerName: this.translate.instant('pricing.policies.col.assignments_count'),
        headerTooltip: this.translate.instant('pricing.policies.col.assignments_count'),
        field: 'assignmentsCount',
        width: 110,
        sortable: true,
        cellRenderer: (params: any) => {
          const count = params.value ?? 0;
          if (count === 0) {
            return `<span class="font-mono text-[var(--text-tertiary)]">0</span>`;
          }
          return `<span class="font-mono cursor-pointer text-[var(--accent-primary)] hover:underline" data-action="assignments">${count}</span>`;
        },
        onCellClicked: (params: any) => {
          const target = params.event?.target as HTMLElement;
          if (target?.closest('[data-action="assignments"]') && params.data) {
            this.navigateToAssignments(params.data as PricingPolicySummary);
          }
        },
      },
      {
        headerName: this.translate.instant('pricing.policies.col.version'),
        headerTooltip: this.translate.instant('pricing.policies.col.version'),
        field: 'version',
        width: 80,
        sortable: true,
        cellClass: 'font-mono text-center',
        valueFormatter: (params: any) =>
          params.value != null ? `v${params.value}` : '',
      },
      {
        headerName: this.translate.instant('pricing.policies.col.updated_at'),
        headerTooltip: this.translate.instant('pricing.policies.col.updated_at'),
        field: 'updatedAt',
        width: 130,
        sortable: true,
        valueFormatter: (params: any) => formatRelativeTime(params.value),
      },
      {
        headerName: '',
        field: '_actions',
        width: 120,
        sortable: false,
        suppressMovable: true,
        cellRenderer: (params: any) => {
          if (!params.data) return '';
          const p = params.data as PricingPolicySummary;
          const editIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"/></svg>`;
          const playIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="6 3 20 12 6 21 6 3"/></svg>`;
          const pauseIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="14" y="4" width="4" height="16" rx="1"/><rect x="6" y="4" width="4" height="16" rx="1"/></svg>`;
          const archiveIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="20" height="5" x="2" y="3" rx="1"/><path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8"/><path d="M10 12h4"/></svg>`;
          let buttons = `<button class="action-btn" data-action="edit" title="${this.translate.instant('actions.edit')}">${editIcon}</button>`;
          if (p.status === 'DRAFT' || p.status === 'PAUSED') {
            buttons += `<button class="action-btn" data-action="activate" title="${this.translate.instant('actions.activate')}">${playIcon}</button>`;
          }
          if (p.status === 'ACTIVE') {
            buttons += `<button class="action-btn" data-action="pause" title="${this.translate.instant('actions.pause')}">${pauseIcon}</button>`;
          }
          if (p.status !== 'ARCHIVED') {
            buttons += `<button class="action-btn" data-action="archive" title="${this.translate.instant('actions.archive')}">${archiveIcon}</button>`;
          }
          return `<div class="flex items-center gap-0.5">${buttons}</div>`;
        },
        onCellClicked: (params: any) => {
          const target = params.event?.target as HTMLElement;
          const action = target?.closest('[data-action]')?.getAttribute('data-action');
          if (!action || !params.data) return;
          const policy = params.data as PricingPolicySummary;
          switch (action) {
            case 'edit':
              this.navigateToEdit(policy);
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
    ].filter((col: any) => col.field !== '_actions' || this.rbac.canWritePolicies());
  });

  private readonly filter = computed<PricingFilter>(() => {
    const vals = this.listState.filterValues();
    const f: PricingFilter = {};
    if (vals['status']?.length) f.status = vals['status'];
    if (vals['strategyType']) f.strategyType = vals['strategyType'];
    if (vals['executionMode']?.length) f.executionMode = vals['executionMode'];
    return f;
  });

  readonly policiesQuery = injectQuery(() => ({
    queryKey: ['policies', this.wsStore.currentWorkspaceId(), this.filter()],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.listPolicies(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          0,
          100,
          'createdAt,desc',
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.policiesQuery.data()?.content ?? []);

  readonly statusSummary = computed(() => {
    const all = this.policiesQuery.data()?.content;
    if (!all?.length) return [];
    const counts: Record<string, number> = {};
    for (const p of all) {
      counts[p.status] = (counts[p.status] ?? 0) + 1;
    }
    return (['ACTIVE', 'PAUSED', 'DRAFT', 'ARCHIVED'] as const)
      .filter((s) => counts[s])
      .map((s) => ({
        status: s,
        count: counts[s],
        label: this.translate.instant('pricing.policies.status.' + s),
        color: `var(--status-${STATUS_COLOR[s]})`,
      }));
  });

  readonly activateMessage = computed(() => {
    const p = this.activateTarget();
    return p
      ? this.translate.instant('pricing.policies.activate_message', { name: p.name })
      : '';
  });

  readonly pauseMessage = computed(() => {
    const p = this.pauseTarget();
    return p
      ? this.translate.instant('pricing.policies.pause_message', { name: p.name })
      : '';
  });

  readonly archiveMessage = computed(() => {
    const p = this.archiveTarget();
    return p
      ? this.translate.instant('pricing.policies.archive_message', { name: p.name })
      : '';
  });

  private readonly activateMutation = injectMutation(() => ({
    mutationFn: (policyId: number) =>
      lastValueFrom(
        this.pricingApi.activatePolicy(this.wsStore.currentWorkspaceId()!, policyId),
      ),
    onSuccess: () => {
      this.showActivateModal.set(false);
      this.activateTarget.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['policies'] });
      this.toast.success(this.translate.instant('pricing.policies.activated'));
    },
    onError: () => {
      this.showActivateModal.set(false);
      this.toast.error(this.translate.instant('pricing.policies.activate_error'));
    },
  }));

  private readonly pauseMutation = injectMutation(() => ({
    mutationFn: (policyId: number) =>
      lastValueFrom(
        this.pricingApi.pausePolicy(this.wsStore.currentWorkspaceId()!, policyId),
      ),
    onSuccess: () => {
      this.showPauseModal.set(false);
      this.pauseTarget.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['policies'] });
      this.toast.success(this.translate.instant('pricing.policies.paused'));
    },
    onError: () => {
      this.showPauseModal.set(false);
      this.toast.error(this.translate.instant('pricing.policies.pause_error'));
    },
  }));

  private readonly archiveMutation = injectMutation(() => ({
    mutationFn: (policyId: number) =>
      lastValueFrom(
        this.pricingApi.archivePolicy(this.wsStore.currentWorkspaceId()!, policyId),
      ),
    onSuccess: () => {
      this.showArchiveModal.set(false);
      this.archiveTarget.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['policies'] });
      this.toast.success(this.translate.instant('pricing.policies.archived'));
    },
    onError: () => {
      this.showArchiveModal.set(false);
      this.toast.error(this.translate.instant('pricing.policies.archive_error'));
    },
  }));

  readonly getRowId = (params: any) => String(params.data.id);

  onRowClicked(row: PricingPolicySummary): void {
    this.navigateToEdit(row);
  }

  navigateToCreate(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'pricing', 'policies', 'new']);
  }

  executeActivate(): void {
    const t = this.activateTarget();
    if (t) this.activateMutation.mutate(t.id);
  }

  executePause(): void {
    const t = this.pauseTarget();
    if (t) this.pauseMutation.mutate(t.id);
  }

  executeArchive(): void {
    const t = this.archiveTarget();
    if (t) this.archiveMutation.mutate(t.id);
  }

  private navigateToEdit(policy: PricingPolicySummary): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate([
      '/workspace', wsId, 'pricing', 'policies', policy.id, 'edit',
    ]);
  }

  navigateToAssignments(policy: PricingPolicySummary): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(
      ['/workspace', wsId, 'pricing', 'policies', policy.id, 'edit'],
      { fragment: 'section-assignments' },
    );
  }
}
