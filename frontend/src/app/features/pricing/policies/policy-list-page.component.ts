import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { LowerCasePipe } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

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
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { formatRelativeTime } from '@shared/utils/format.utils';

const POLICY_STATUSES = ['DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED'] as const;
const EXECUTION_MODES = [
  'RECOMMENDATION', 'SEMI_AUTO', 'FULL_AUTO', 'SIMULATED',
] as const;
const STRATEGY_TYPES = [
  'TARGET_MARGIN', 'PRICE_CORRIDOR', 'VELOCITY_ADAPTIVE',
  'STOCK_BALANCING', 'COMPOSITE', 'COMPETITOR_ANCHOR',
] as const;

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'var(--status-info)',
  ACTIVE: 'var(--status-success)',
  PAUSED: 'var(--status-warning)',
  ARCHIVED: 'var(--text-tertiary)',
};

const MODE_COLOR: Record<string, string> = {
  RECOMMENDATION: 'var(--status-info)',
  SEMI_AUTO: 'var(--status-warning)',
  FULL_AUTO: 'var(--status-success)',
  SIMULATED: 'var(--status-neutral)',
};

@Component({
  selector: 'dp-policy-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    LowerCasePipe,
    FilterBarComponent,
    EmptyStateComponent,
    ConfirmationModalComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  templateUrl: './policy-list-page.component.html',
  styles: [`
    .policy-row {
      border-left: 3px solid transparent;
      transition: background-color 150ms ease, border-color 150ms ease;
    }
    .policy-row:hover { background-color: var(--bg-secondary); }
    .policy-row:hover .row-actions { opacity: 1; }
    .row-actions { opacity: 0; transition: opacity 150ms ease; }
    .action-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border-radius: var(--radius-sm);
      color: var(--text-tertiary);
      cursor: pointer;
      transition: background-color 120ms ease, color 120ms ease;
    }
    .action-icon:hover { background-color: var(--bg-tertiary); color: var(--text-primary); }
    .action-icon.success:hover { color: var(--status-success); }
    .action-icon.warning:hover { color: var(--status-warning); }
    .action-icon.danger:hover { color: var(--status-error); }
  `],
})
export class PolicyListPageComponent {
  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  private readonly tourService = inject(GuidedTourService);
  private readonly tourProgress = inject(TourProgressStore);

  constructor() {
    if (PRICING_POLICIES_TOUR.triggerOnFirstVisit && !this.tourProgress.isCompleted(PRICING_POLICIES_TOUR.id)) {
      setTimeout(() => this.tourService.start(PRICING_POLICIES_TOUR), 1200);
    }
  }
  private readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  readonly filterValues = signal<Record<string, any>>({
    status: ['DRAFT', 'ACTIVE', 'PAUSED'],
  });
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

  private readonly filter = computed<PricingFilter>(() => {
    const vals = this.filterValues();
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

  readonly hasActiveFilters = computed(() =>
    Object.values(this.filterValues()).some(
      (v) =>
        v !== '' &&
        v !== null &&
        v !== undefined &&
        (!Array.isArray(v) || v.length > 0),
    ),
  );

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
        color: STATUS_COLOR[s],
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

  statusColor(status: string): string {
    return STATUS_COLOR[status] ?? 'var(--text-tertiary)';
  }

  statusBg(status: string): string {
    return `color-mix(in srgb, ${this.statusColor(status)} 12%, transparent)`;
  }

  modeColor(mode: string): string {
    return MODE_COLOR[mode] ?? 'var(--text-secondary)';
  }

  modeBg(mode: string): string {
    return `color-mix(in srgb, ${this.modeColor(mode)} 12%, transparent)`;
  }

  relativeTime(iso: string): string {
    return formatRelativeTime(iso);
  }

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
  }

  navigateToEdit(policy: PricingPolicySummary, event: MouseEvent): void {
    event.stopPropagation();
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate([
      '/workspace', wsId, 'pricing', 'policies', policy.id, 'edit',
    ]);
  }

  navigateToCreate(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'pricing', 'policies', 'new']);
  }

  startActivate(policy: PricingPolicySummary, event: MouseEvent): void {
    event.stopPropagation();
    this.activateTarget.set(policy);
    this.showActivateModal.set(true);
  }

  startPause(policy: PricingPolicySummary, event: MouseEvent): void {
    event.stopPropagation();
    this.pauseTarget.set(policy);
    this.showPauseModal.set(true);
  }

  startArchive(policy: PricingPolicySummary, event: MouseEvent): void {
    event.stopPropagation();
    this.archiveTarget.set(policy);
    this.showArchiveModal.set(true);
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
}
