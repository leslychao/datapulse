import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  OnInit,
  OnDestroy,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery, injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom, Subscription } from 'rxjs';
import { LucideAngularModule, ArrowLeft, ChevronDown, ChevronUp } from 'lucide-angular';

import { ActionApiService } from '@core/api/action-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { WebSocketService } from '@core/websocket/websocket.service';
import { formatMoney, formatDateTime } from '@shared/utils/format.utils';
import { ReconcileRequest } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { BreadcrumbService } from '@shared/services/breadcrumb.service';
import { ToastService } from '@shared/shell/toast/toast.service';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { FormModalComponent } from '@shared/components/form-modal.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { FormsModule } from '@angular/forms';

const STATUS_COLOR: Record<string, StatusColor> = {
  PENDING_APPROVAL: 'info', APPROVED: 'info', ON_HOLD: 'warning',
  SCHEDULED: 'info', EXECUTING: 'warning', RECONCILIATION_PENDING: 'warning',
  RETRY_SCHEDULED: 'warning', SUCCEEDED: 'success', FAILED: 'error',
  EXPIRED: 'neutral', CANCELLED: 'neutral', SUPERSEDED: 'neutral',
};

const MAIN_FLOW = [
  'PENDING_APPROVAL', 'APPROVED', 'SCHEDULED', 'EXECUTING',
  'RECONCILIATION_PENDING', 'SUCCEEDED',
];

const BRANCH_STATES: Record<string, string[]> = {
  PENDING_APPROVAL: ['EXPIRED', 'SUPERSEDED', 'CANCELLED'],
  APPROVED: ['ON_HOLD', 'CANCELLED'],
  EXECUTING: ['RETRY_SCHEDULED', 'FAILED'],
  RECONCILIATION_PENDING: ['CANCELLED'],
};

const TERMINAL_STATES = new Set([
  'SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED',
]);

const CANCEL_DESTRUCTIVE_STATUSES = new Set(['RECONCILIATION_PENDING']);

const OUTCOME_COLOR: Record<string, StatusColor> = {
  SUCCESS: 'success', RETRY: 'warning', FAILURE: 'error', INDETERMINATE: 'warning',
};

@Component({
  selector: 'dp-action-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe, LucideAngularModule, StatusBadgeComponent,
    FormModalComponent, ConfirmationModalComponent, FormsModule, RouterLink,
  ],
  templateUrl: './action-detail-page.component.html',
})
export class ActionDetailPageComponent implements OnInit, OnDestroy {
  readonly actionId = input.required<string>();

  private readonly actionApi = inject(ActionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  private readonly translate = inject(TranslateService);
  private readonly webSocket = inject(WebSocketService);
  private readonly breadcrumbs = inject(BreadcrumbService);
  protected readonly rbac = inject(RbacService);

  private actionSub: Subscription | null = null;

  constructor() {
    effect(() => {
      const a = this.action();
      if (!a) return;
      const wsId = this.wsStore.currentWorkspaceId();
      const base = `/workspace/${wsId}/execution`;
      this.breadcrumbs.setSegments([
        { label: this.translate.instant('execution.nav.title'), route: base },
        { label: this.translate.instant('execution.nav.actions'), route: `${base}/actions` },
        { label: `${a.offerName} (${a.sku})`, route: null },
      ]);
    });
  }

  protected readonly ArrowLeft = ArrowLeft;
  protected readonly ChevronDown = ChevronDown;
  protected readonly ChevronUp = ChevronUp;

  readonly reasonText = signal('');
  readonly showRejectModal = signal(false);
  readonly showCancelModal = signal(false);
  readonly showRetryModal = signal(false);
  readonly showReconcileModal = signal(false);
  readonly holdInline = signal(false);
  readonly expandedAttempt = signal<number | null>(null);
  readonly actionPending = signal(false);

  readonly cancelConfirmText = signal('');
  readonly reconcileOutcome = signal<'SUCCEEDED' | 'FAILED'>('SUCCEEDED');
  readonly reconcileReason = signal('');
  readonly reconcileConfirmText = signal('');

  private readonly numericId = computed(() => Number(this.actionId()));
  private readonly isTerminal = computed(() => TERMINAL_STATES.has(this.status()));

  readonly actionQuery = injectQuery(() => ({
    queryKey: ['action', this.numericId()],
    queryFn: () =>
      lastValueFrom(
        this.actionApi.getAction(this.wsStore.currentWorkspaceId()!, this.numericId()),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !isNaN(this.numericId()),
    staleTime: 10_000,
    refetchInterval: this.isTerminal() ? false : 15_000,
  }));

  readonly action = computed(() => this.actionQuery.data() ?? null);
  readonly status = computed(() => this.action()?.status ?? '');

  readonly statusLabel = computed(() =>
    this.translate.instant(`grid.action_status.${this.status()}`) || this.status(),
  );
  readonly statusColor = computed(() => STATUS_COLOR[this.status()] ?? 'neutral');

  readonly statusContext = computed(() => {
    const a = this.action();
    if (!a) return '';
    switch (a.status) {
      case 'PENDING_APPROVAL':
        return this.translate.instant('execution.detail.ctx.pending_approval');
      case 'APPROVED':
        return a.approvedBy
          ? this.translate.instant('execution.detail.ctx.approved', { name: a.approvedBy, at: this.formatTimestamp(a.updatedAt) })
          : '';
      case 'ON_HOLD':
        return a.holdReason
          ? this.translate.instant('execution.detail.ctx.on_hold', { reason: a.holdReason })
          : '';
      case 'EXECUTING':
        return this.translate.instant('execution.detail.ctx.executing', { attempt: a.attemptCount, max: a.maxAttempts });
      case 'RECONCILIATION_PENDING':
        return this.translate.instant('execution.detail.ctx.reconciliation_pending');
      case 'RETRY_SCHEDULED':
        return this.translate.instant('execution.detail.ctx.retry_scheduled');
      case 'FAILED': {
        const last = (a.attempts ?? []).at(-1);
        return last?.errorMessage
          ? this.translate.instant('execution.detail.ctx.failed', { error: last.errorMessage })
          : '';
      }
      case 'SUCCEEDED': {
        const lastAttempt = (a.attempts ?? []).at(-1);
        return lastAttempt?.reconciliationSource
          ? this.translate.instant('execution.detail.ctx.succeeded', { source: lastAttempt.reconciliationSource })
          : '';
      }
      default:
        return '';
    }
  });

  readonly deltaPctDisplay = computed(() => {
    const d = this.action()?.priceDeltaPct;
    if (d === null || d === undefined) return '—';
    const abs = Math.abs(d).toFixed(1).replace('.', ',');
    if (d > 0) return `↑ ${abs}%`;
    if (d < 0) return `↓ ${abs}%`;
    return `→ 0%`;
  });

  readonly deltaPctColor = computed(() => {
    const d = this.action()?.priceDeltaPct;
    if (d === null || d === undefined || d === 0) return 'var(--finance-zero)';
    return d > 0 ? 'var(--finance-positive)' : 'var(--finance-negative)';
  });

  readonly canApprove = computed(() => this.status() === 'PENDING_APPROVAL' && this.rbac.canApproveActions());
  readonly canReject = computed(() => this.status() === 'PENDING_APPROVAL' && this.rbac.canApproveActions());
  readonly canHold = computed(() => this.status() === 'APPROVED' && this.rbac.canOperateActions());
  readonly canResume = computed(() => this.status() === 'ON_HOLD' && this.rbac.canOperateActions());
  readonly canRetry = computed(() => this.status() === 'FAILED' && this.rbac.canApproveActions());
  readonly canCancel = computed(() =>
    ['PENDING_APPROVAL', 'APPROVED', 'ON_HOLD', 'SCHEDULED', 'RETRY_SCHEDULED', 'RECONCILIATION_PENDING']
      .includes(this.status()) && this.rbac.canOperateActions(),
  );
  readonly canReconcile = computed(() =>
    this.status() === 'RECONCILIATION_PENDING' && this.rbac.canReconcileActions(),
  );

  readonly hasAnyAction = computed(() =>
    this.canApprove() || this.canReject() || this.canHold() || this.canResume()
    || this.canCancel() || this.canRetry() || this.canReconcile(),
  );

  readonly isReadOnlyRole = computed(() => {
    const role = this.rbac.currentRole();
    return role === 'VIEWER' || role === 'ANALYST';
  });

  readonly isCancelDestructive = computed(() =>
    CANCEL_DESTRUCTIVE_STATUSES.has(this.status()),
  );

  readonly mainFlowNodes = computed(() => {
    const current = this.status();
    const history = this.action()?.stateHistory ?? [];
    const visitedSet = new Set(history.map((h) => h.toStatus));
    visitedSet.add(current);

    return MAIN_FLOW.map((s, i) => {
      const isCurrent = s === current;
      const isPast = !isCurrent && visitedSet.has(s);
      const isFuture = !isCurrent && !isPast;
      const branches = (BRANCH_STATES[s] ?? []).map(b => ({
        status: b,
        label: this.translate.instant(`grid.action_status.${b}`),
        color: STATUS_COLOR[b] ?? 'neutral',
        isCurrent: b === current,
        isVisited: visitedSet.has(b),
      }));
      return {
        status: s,
        label: this.translate.instant(`grid.action_status.${s}`),
        color: STATUS_COLOR[s] ?? 'neutral',
        isCurrent,
        isPast,
        isFuture,
        branches,
        stepNum: i + 1,
        totalSteps: MAIN_FLOW.length,
      };
    });
  });

  readonly attempts = computed(() => this.action()?.attempts ?? []);

  readonly approveMutation = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(this.actionApi.approveAction(this.wsStore.currentWorkspaceId()!, this.numericId())),
    onSuccess: () => this.afterSuccess('execution.detail.toast.approved'),
    onError: (err: HttpErrorResponse) => this.handleError(err),
  }));

  readonly rejectMutation = injectMutation(() => ({
    mutationFn: (reason: string) =>
      lastValueFrom(this.actionApi.rejectAction(this.wsStore.currentWorkspaceId()!, this.numericId(), reason)),
    onSuccess: () => { this.showRejectModal.set(false); this.afterSuccess('execution.detail.toast.rejected'); },
    onError: (err: HttpErrorResponse) => { this.showRejectModal.set(false); this.handleError(err); },
  }));

  readonly holdMutation = injectMutation(() => ({
    mutationFn: (reason: string) =>
      lastValueFrom(this.actionApi.holdAction(this.wsStore.currentWorkspaceId()!, this.numericId(), reason)),
    onSuccess: () => { this.holdInline.set(false); this.afterSuccess('execution.detail.toast.held'); },
    onError: (err: HttpErrorResponse) => { this.holdInline.set(false); this.handleError(err); },
  }));

  readonly resumeMutation = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(this.actionApi.resumeAction(this.wsStore.currentWorkspaceId()!, this.numericId())),
    onSuccess: () => this.afterSuccess('execution.detail.toast.resumed'),
    onError: (err: HttpErrorResponse) => this.handleError(err),
  }));

  readonly cancelMutation = injectMutation(() => ({
    mutationFn: (reason: string) =>
      lastValueFrom(this.actionApi.cancelAction(this.wsStore.currentWorkspaceId()!, this.numericId(), reason)),
    onSuccess: () => { this.showCancelModal.set(false); this.afterSuccess('execution.detail.toast.cancelled'); },
    onError: (err: HttpErrorResponse) => { this.showCancelModal.set(false); this.handleError(err); },
  }));

  readonly retryMutation = injectMutation(() => ({
    mutationFn: (reason: string) =>
      lastValueFrom(this.actionApi.retryAction(this.wsStore.currentWorkspaceId()!, this.numericId(), reason)),
    onSuccess: () => { this.showRetryModal.set(false); this.afterSuccess('execution.detail.toast.retried'); },
    onError: (err: HttpErrorResponse) => { this.showRetryModal.set(false); this.handleError(err); },
  }));

  readonly reconcileMutation = injectMutation(() => ({
    mutationFn: (req: ReconcileRequest) =>
      lastValueFrom(this.actionApi.reconcileAction(this.wsStore.currentWorkspaceId()!, this.numericId(), req)),
    onSuccess: () => { this.showReconcileModal.set(false); this.afterSuccess('execution.detail.toast.reconciled'); },
    onError: (err: HttpErrorResponse) => { this.showReconcileModal.set(false); this.handleError(err); },
  }));

  ngOnInit(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    const aId = this.numericId();
    if (wsId && !isNaN(aId)) {
      this.actionSub = this.webSocket.subscribeToAction(wsId, aId);
    }
  }

  ngOnDestroy(): void {
    this.actionSub?.unsubscribe();
  }

  goBack(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'execution', 'actions']);
  }

  approve(): void {
    this.actionPending.set(true);
    this.approveMutation.mutate(undefined);
  }

  resume(): void {
    this.actionPending.set(true);
    this.resumeMutation.mutate(undefined);
  }

  submitReject(): void {
    this.actionPending.set(true);
    this.rejectMutation.mutate(this.reasonText());
  }

  submitHold(): void {
    this.actionPending.set(true);
    this.holdMutation.mutate(this.reasonText());
  }

  submitCancel(): void {
    this.actionPending.set(true);
    this.cancelMutation.mutate(this.reasonText());
  }

  submitRetry(): void {
    this.actionPending.set(true);
    this.retryMutation.mutate(this.reasonText());
  }

  submitReconcile(): void {
    this.actionPending.set(true);
    this.reconcileMutation.mutate({
      outcome: this.reconcileOutcome(),
      manualOverrideReason: this.reconcileReason(),
    });
  }

  openReasonModal(type: 'reject' | 'cancel' | 'retry'): void {
    this.reasonText.set('');
    this.cancelConfirmText.set('');
    if (type === 'reject') this.showRejectModal.set(true);
    else if (type === 'cancel') this.showCancelModal.set(true);
    else this.showRetryModal.set(true);
  }

  openHoldInline(): void {
    this.reasonText.set('');
    this.holdInline.set(true);
  }

  openReconcileModal(): void {
    this.reconcileOutcome.set('SUCCEEDED');
    this.reconcileReason.set('');
    this.reconcileConfirmText.set('');
    this.showReconcileModal.set(true);
  }

  toggleAttemptDetail(attemptNumber: number): void {
    this.expandedAttempt.set(
      this.expandedAttempt() === attemptNumber ? null : attemptNumber,
    );
  }

  formatTimestamp(iso: string | null): string {
    return formatDateTime(iso, 'full');
  }

  formatDuration(start: string | null, end: string | null): string {
    if (!start || !end) return '—';
    const ms = new Date(end).getTime() - new Date(start).getTime();
    if (ms < 1000) return `${ms} ${this.translate.instant('common.time.ms')}`;
    return `${(ms / 1000).toFixed(1).replace('.', ',')} ${this.translate.instant('common.time.sec')}`;
  }

  formatPrice(value: number | null): string {
    return formatMoney(value, 0);
  }

  outcomeLabel(outcome: string): string {
    return this.translate.instant(`execution.detail.outcome.${outcome}`);
  }

  outcomeColor(outcome: string): StatusColor {
    return OUTCOME_COLOR[outcome] ?? 'neutral';
  }

  formatJson(raw: string | null): string {
    if (!raw) return '—';
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      return raw;
    }
  }

  private afterSuccess(msgKey: string): void {
    this.actionPending.set(false);
    this.reasonText.set('');
    this.queryClient.invalidateQueries({ queryKey: ['action', this.numericId()] });
    this.queryClient.invalidateQueries({ queryKey: ['actions'] });
    this.toast.success(this.translate.instant(msgKey));
  }

  private handleError(err: HttpErrorResponse): void {
    this.actionPending.set(false);
    if (err.status === 409) {
      this.toast.warning(this.translate.instant('execution.detail.cas_conflict'));
      this.queryClient.invalidateQueries({ queryKey: ['action', this.numericId()] });
      this.queryClient.invalidateQueries({ queryKey: ['actions'] });
      this.resetModals();
    } else {
      this.toast.error(this.translate.instant('execution.detail.action_error'));
    }
  }

  private resetModals(): void {
    this.showRejectModal.set(false);
    this.showCancelModal.set(false);
    this.showRetryModal.set(false);
    this.showReconcileModal.set(false);
    this.holdInline.set(false);
    this.reasonText.set('');
    this.reconcileReason.set('');
    this.reconcileConfirmText.set('');
  }
}
