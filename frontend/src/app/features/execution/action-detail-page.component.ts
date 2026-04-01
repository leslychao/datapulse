import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery, injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, ArrowLeft, ChevronDown, ChevronUp } from 'lucide-angular';

import { ActionApiService } from '@core/api/action-api.service';
import { formatMoney, formatDateTime } from '@shared/utils/format.utils';
import { ActionDetail, ActionAttempt } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { FormModalComponent } from '@shared/components/form-modal.component';
import { FormsModule } from '@angular/forms';

const STATUS_COLOR: Record<string, StatusColor> = {
  PENDING_APPROVAL: 'info',
  APPROVED: 'info',
  ON_HOLD: 'warning',
  SCHEDULED: 'info',
  EXECUTING: 'warning',
  RECONCILIATION_PENDING: 'warning',
  RETRY_SCHEDULED: 'warning',
  SUCCEEDED: 'success',
  FAILED: 'error',
  EXPIRED: 'neutral',
  CANCELLED: 'neutral',
  SUPERSEDED: 'neutral',
};

const MAIN_FLOW = [
  'PENDING_APPROVAL',
  'APPROVED',
  'SCHEDULED',
  'EXECUTING',
  'RECONCILIATION_PENDING',
  'SUCCEEDED',
];

const OUTCOME_COLOR: Record<string, StatusColor> = {
  SUCCESS: 'success',
  RETRY: 'warning',
  FAILURE: 'error',
  INDETERMINATE: 'warning',
};

@Component({
  selector: 'dp-action-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    LucideAngularModule,
    StatusBadgeComponent,
    FormModalComponent,
    FormsModule,
  ],
  templateUrl: './action-detail-page.component.html',
})
export class ActionDetailPageComponent {
  readonly actionId = input.required<string>();

  private readonly actionApi = inject(ActionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  private readonly translate = inject(TranslateService);

  protected readonly ArrowLeft = ArrowLeft;
  protected readonly ChevronDown = ChevronDown;
  protected readonly ChevronUp = ChevronUp;

  readonly reasonText = signal('');
  readonly showRejectModal = signal(false);
  readonly showCancelModal = signal(false);
  readonly showRetryModal = signal(false);
  readonly expandedAttempt = signal<number | null>(null);
  readonly actionPending = signal(false);

  private readonly numericId = computed(() => Number(this.actionId()));

  readonly actionQuery = injectQuery(() => ({
    queryKey: ['action', this.numericId()],
    queryFn: () =>
      lastValueFrom(
        this.actionApi.getAction(this.wsStore.currentWorkspaceId()!, this.numericId()),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !isNaN(this.numericId()),
    staleTime: 10_000,
    refetchInterval: 15_000,
  }));

  readonly action = computed(() => this.actionQuery.data() ?? null);
  readonly status = computed(() => this.action()?.status ?? '');

  readonly statusLabel = computed(() =>
    this.translate.instant(`grid.action_status.${this.status()}`) || this.status(),
  );
  readonly statusColor = computed(() => STATUS_COLOR[this.status()] ?? 'neutral');

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

  readonly canApprove = computed(() => this.status() === 'PENDING_APPROVAL');
  readonly canReject = computed(() => this.status() === 'PENDING_APPROVAL');
  readonly canHold = computed(() => this.status() === 'APPROVED');
  readonly canResume = computed(() => this.status() === 'ON_HOLD');
  readonly canRetry = computed(() => this.status() === 'FAILED');
  readonly canCancel = computed(() =>
    ['PENDING_APPROVAL', 'APPROVED', 'ON_HOLD', 'SCHEDULED', 'RETRY_SCHEDULED', 'RECONCILIATION_PENDING'].includes(this.status()),
  );

  readonly mainFlowNodes = computed(() => {
    const current = this.status();
    const history = this.action()?.stateHistory ?? [];
    const visitedSet = new Set(history.map((h) => h.toStatus));
    visitedSet.add(current);

    return MAIN_FLOW.map((s) => {
      const isCurrent = s === current;
      const isPast = !isCurrent && visitedSet.has(s);
      const isFuture = !isCurrent && !isPast;
      return {
        status: s,
        label: this.translate.instant(`grid.action_status.${s}`),
        color: STATUS_COLOR[s] ?? 'neutral',
        isCurrent,
        isPast,
        isFuture,
      };
    });
  });

  readonly attempts = computed(() => this.action()?.attempts ?? []);

  private createMutation(
    mutationFn: (id: number, reason?: string) => any,
    successMsg: string,
    needsReason = false,
  ) {
    return injectMutation(() => ({
      mutationFn: (data: { id: number; reason?: string }) =>
        lastValueFrom(needsReason ? mutationFn(data.id, data.reason!) : mutationFn(data.id)),
      onSuccess: () => {
        this.actionPending.set(false);
        this.queryClient.invalidateQueries({ queryKey: ['action', this.numericId()] });
        this.queryClient.invalidateQueries({ queryKey: ['actions'] });
        this.toast.success(successMsg);
        this.resetModals();
      },
      onError: () => {
        this.actionPending.set(false);
        this.toast.error(this.translate.instant('execution.detail.action_error'));
        this.resetModals();
      },
    }));
  }

  readonly approveMutation = this.createMutation(
    (id) => this.actionApi.approveAction(this.wsStore.currentWorkspaceId()!, id),
    this.translate.instant('execution.detail.toast.approved'),
  );

  readonly rejectMutation = this.createMutation(
    (id, reason) => this.actionApi.rejectAction(this.wsStore.currentWorkspaceId()!, id, reason!),
    this.translate.instant('execution.detail.toast.rejected'),
    true,
  );

  readonly holdMutation = this.createMutation(
    (id, reason) => this.actionApi.holdAction(this.wsStore.currentWorkspaceId()!, id, reason!),
    this.translate.instant('execution.detail.toast.held'),
    true,
  );

  readonly resumeMutation = this.createMutation(
    (id) => this.actionApi.resumeAction(this.wsStore.currentWorkspaceId()!, id),
    this.translate.instant('execution.detail.toast.resumed'),
  );

  readonly cancelMutation = this.createMutation(
    (id, reason) => this.actionApi.cancelAction(this.wsStore.currentWorkspaceId()!, id, reason!),
    this.translate.instant('execution.detail.toast.cancelled'),
    true,
  );

  readonly retryMutation = this.createMutation(
    (id, reason) => this.actionApi.retryAction(this.wsStore.currentWorkspaceId()!, id, reason!),
    this.translate.instant('execution.detail.toast.retried'),
    true,
  );

  goBack(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'execution', 'actions']);
  }

  approve(): void {
    this.actionPending.set(true);
    this.approveMutation.mutate({ id: this.numericId() });
  }

  resume(): void {
    this.actionPending.set(true);
    this.resumeMutation.mutate({ id: this.numericId() });
  }

  submitReject(): void {
    this.actionPending.set(true);
    this.rejectMutation.mutate({ id: this.numericId(), reason: this.reasonText() });
  }

  submitHold(): void {
    this.actionPending.set(true);
    this.holdMutation.mutate({ id: this.numericId(), reason: this.reasonText() });
  }

  submitCancel(): void {
    this.actionPending.set(true);
    this.cancelMutation.mutate({ id: this.numericId(), reason: this.reasonText() });
  }

  submitRetry(): void {
    this.actionPending.set(true);
    this.retryMutation.mutate({ id: this.numericId(), reason: this.reasonText() });
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

  openReasonModal(type: 'reject' | 'cancel' | 'retry'): void {
    this.reasonText.set('');
    if (type === 'reject') this.showRejectModal.set(true);
    else if (type === 'cancel') this.showCancelModal.set(true);
    else this.showRetryModal.set(true);
  }

  private resetModals(): void {
    this.showRejectModal.set(false);
    this.showCancelModal.set(false);
    this.showRetryModal.set(false);
    this.reasonText.set('');
  }
}
