import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PromoApiService } from '@core/api/promo-api.service';
import { formatRelativeTime } from '@shared/utils/format.utils';
import {
  ParticipationMode,
  PromoPolicyFilter,
  PromoPolicySummary,
  PromoPolicyStatus,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { RbacService } from '@core/auth/rbac.service';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';

const POLICY_STATUS_COLOR: Record<PromoPolicyStatus, string> = {
  DRAFT: 'neutral',
  ACTIVE: 'success',
  PAUSED: 'warning',
  ARCHIVED: 'neutral',
};

const POLICY_STATUSES: PromoPolicyStatus[] = [
  'DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED',
];

const PARTICIPATION_MODES: ParticipationMode[] = [
  'RECOMMENDATION', 'SEMI_AUTO', 'FULL_AUTO', 'SIMULATED',
];

@Component({
  selector: 'dp-promo-policy-list-page',
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
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <h2 class="text-base font-semibold text-[var(--text-primary)]">
          {{ 'promo.policies.title' | translate }}
        </h2>
        @if (rbac.canWritePromo()) {
          <button
            (click)="navigateToCreate()"
            class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
          >
            {{ 'promo.policies.create' | translate }}
          </button>
        }
      </div>

      <div class="px-6 pt-2">
        <dp-filter-bar
          [filters]="filterConfigs"
          [values]="filterValues()"
          (filtersChanged)="onFiltersChanged($event)"
        />
      </div>

      <div class="flex-1 px-4 py-2">
        @if (policiesQuery.isError()) {
          <dp-empty-state
            [message]="'promo.policies.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="policiesQuery.refetch()"
          />
        } @else if (!policiesQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="hasActiveFilters()
              ? ('promo.policies.empty_filtered' | translate)
              : ('promo.policies.empty' | translate)"
            [actionLabel]="hasActiveFilters()
              ? ('filter_bar.reset_all' | translate)
              : ('promo.policies.create' | translate)"
            (action)="hasActiveFilters() ? onFiltersChanged({}) : navigateToCreate()"
          />
        } @else {
          <dp-data-grid
            [columnDefs]="columnDefs()"
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

    <dp-confirmation-modal
      [open]="showArchiveModal()"
      [title]="'promo.policies.archive_title' | translate"
      [message]="archiveMessage()"
      [confirmLabel]="'promo.policies.archive_confirm' | translate"
      [danger]="true"
      (confirmed)="executeArchive()"
      (cancelled)="showArchiveModal.set(false)"
    />
  `,
})
export class PromoPolicyListPageComponent {
  private readonly promoApi = inject(PromoApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  private readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  readonly filterValues = signal<Record<string, any>>({
    status: ['DRAFT', 'ACTIVE', 'PAUSED'],
  });
  readonly currentPage = signal(0);

  readonly showArchiveModal = signal(false);
  readonly archiveTarget = signal<PromoPolicySummary | null>(null);

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: 'grid.filter.status',
      type: 'multi-select',
      options: POLICY_STATUSES.map(value => ({
        value,
        label: `promo.policy_status.${value}`,
      })),
    },
    {
      key: 'participationMode',
      label: 'promo.filter.mode',
      type: 'multi-select',
      options: PARTICIPATION_MODES.map(value => ({
        value,
        label: `promo.participation_mode.${value}`,
      })),
    },
    { key: 'search', label: 'promo.filter.search', type: 'text' },
  ];

  readonly columnDefs = computed(() => [
    {
      headerName: this.translate.instant('promo.policies.col.name'),
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
      headerName: this.translate.instant('promo.policies.col.status'),
      field: 'status',
      width: 130,
      sortable: true,
      cellRenderer: (params: any) => {
        const st = params.value as PromoPolicyStatus;
        const label = this.translate.instant(`promo.policy_status.${st}`);
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
      headerName: this.translate.instant('promo.policies.col.mode'),
      field: 'participationMode',
      width: 140,
      sortable: true,
      valueFormatter: (params: any) =>
        this.translate.instant(`promo.participation_mode.${params.value}`),
    },
    {
      headerName: this.translate.instant('promo.policies.col.min_margin'),
      field: 'minMarginPct',
      width: 100,
      cellClass: 'font-mono text-right',
      sortable: true,
      valueFormatter: (params: any) =>
        params.value != null ? `${params.value.toFixed(1).replace('.', ',')}%` : '—',
    },
    {
      headerName: this.translate.instant('promo.policies.col.min_stock_days'),
      field: 'minStockDaysOfCover',
      width: 120,
      cellClass: 'font-mono text-right',
      sortable: true,
      valueFormatter: (params: any) =>
        params.value != null
          ? `${params.value} ${this.translate.instant('common.days_short')}`
          : '—',
    },
    {
      headerName: this.translate.instant('promo.policies.col.max_discount'),
      field: 'maxPromoDiscountPct',
      width: 110,
      cellClass: 'font-mono text-right',
      sortable: true,
      valueFormatter: (params: any) =>
        params.value != null ? `${params.value.toFixed(1).replace('.', ',')}%` : '—',
    },
    {
      headerName: this.translate.instant('promo.policies.col.version'),
      field: 'version',
      width: 70,
      cellClass: 'font-mono text-center',
      sortable: true,
      valueFormatter: (params: any) =>
        params.value != null ? `v${params.value}` : '',
    },
    {
      headerName: this.translate.instant('promo.policies.col.assignments'),
      field: 'assignmentCount',
      width: 100,
      cellClass: 'font-mono text-center',
      sortable: true,
    },
    {
      headerName: this.translate.instant('promo.policies.col.created_by'),
      field: 'createdByName',
      width: 130,
      sortable: true,
    },
    {
      headerName: this.translate.instant('promo.policies.col.updated_at'),
      field: 'updatedAt',
      width: 130,
      sortable: true,
      valueFormatter: (params: any) => this.formatRelativeTime(params.value),
    },
    {
      headerName: '',
      field: '_actions',
      width: 120,
      sortable: false,
      suppressMovable: true,
      cellRenderer: (params: any) => {
        if (!params.data) return '';
        const p = params.data as PromoPolicySummary;
        const editIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"/></svg>`;
        const playIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><polygon points="6 3 20 12 6 21 6 3"/></svg>`;
        const pauseIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><rect x="14" y="4" width="4" height="16" rx="1"/><rect x="6" y="4" width="4" height="16" rx="1"/></svg>`;
        const archiveIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><rect width="20" height="5" x="2" y="3" rx="1"/><path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8"/><path d="M10 12h4"/></svg>`;
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
        const policy = params.data as PromoPolicySummary;
        switch (action) {
          case 'edit':
            this.navigateToEdit(policy.id);
            break;
          case 'activate':
            this.activateMutation.mutate(policy.id);
            break;
          case 'pause':
            this.pauseMutation.mutate(policy.id);
            break;
          case 'archive':
            this.archiveTarget.set(policy);
            this.showArchiveModal.set(true);
            break;
        }
      },
    },
  ].filter((col: any) => col.field !== '_actions' || this.rbac.canWritePromo()));

  private readonly filter = computed<PromoPolicyFilter>(() => {
    const vals = this.filterValues();
    const f: PromoPolicyFilter = {};
    if (vals['status']?.length) f.status = vals['status'];
    if (vals['participationMode']?.length) f.participationMode = vals['participationMode'];
    if (vals['search']) f.search = vals['search'];
    return f;
  });

  readonly policiesQuery = injectQuery(() => ({
    queryKey: [
      'promo-policies',
      this.wsStore.currentWorkspaceId(),
      this.filter(),
      this.currentPage(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.promoApi.listPolicies(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.currentPage(),
          50,
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.policiesQuery.data()?.content ?? []);

  readonly hasActiveFilters = computed(() =>
    Object.values(this.filterValues()).some(
      (v) => v !== '' && v !== null && v !== undefined && (!Array.isArray(v) || v.length > 0),
    ),
  );

  readonly archiveMessage = computed(() => {
    const p = this.archiveTarget();
    return p ? this.translate.instant('promo.policies.archive_message', { name: p.name }) : '';
  });

  private readonly activateMutation = injectMutation(() => ({
    mutationFn: (policyId: number) =>
      lastValueFrom(this.promoApi.activatePolicy(this.wsStore.currentWorkspaceId()!, policyId)),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['promo-policies'] });
      this.toast.success(this.translate.instant('promo.policies.toast.activate_success'));
    },
    onError: () => this.toast.error(this.translate.instant('promo.policies.toast.activate_error')),
  }));

  private readonly pauseMutation = injectMutation(() => ({
    mutationFn: (policyId: number) =>
      lastValueFrom(this.promoApi.pausePolicy(this.wsStore.currentWorkspaceId()!, policyId)),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['promo-policies'] });
      this.toast.success(this.translate.instant('promo.policies.toast.pause_success'));
    },
    onError: () => this.toast.error(this.translate.instant('promo.policies.toast.pause_error')),
  }));

  private readonly archiveMutation = injectMutation(() => ({
    mutationFn: (policyId: number) =>
      lastValueFrom(this.promoApi.archivePolicy(this.wsStore.currentWorkspaceId()!, policyId)),
    onSuccess: () => {
      this.showArchiveModal.set(false);
      this.archiveTarget.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['promo-policies'] });
      this.toast.success(this.translate.instant('promo.policies.toast.archive_success'));
    },
    onError: () => {
      this.showArchiveModal.set(false);
      this.toast.error(this.translate.instant('promo.policies.toast.archive_error'));
    },
  }));

  readonly getRowId = (params: any) => String(params.data.id);

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
    this.currentPage.set(0);
  }

  onRowClicked(row: PromoPolicySummary): void {
    this.navigateToEdit(row.id);
  }

  navigateToCreate(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'promo', 'policies', 'new']);
  }

  executeArchive(): void {
    const target = this.archiveTarget();
    if (target) {
      this.archiveMutation.mutate(target.id);
    }
  }

  private navigateToEdit(policyId: number): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'promo', 'policies', policyId, 'edit']);
  }

  private formatRelativeTime(iso: string | null): string {
    return formatRelativeTime(iso);
  }
}
