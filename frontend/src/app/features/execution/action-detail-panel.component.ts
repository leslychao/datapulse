import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery, injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, ChevronDown, ChevronUp, ExternalLink } from 'lucide-angular';

import { ActionApiService } from '@core/api/action-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { formatMoney, formatDateTime } from '@shared/utils/format.utils';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { DetailPanelService } from '@shared/services/detail-panel.service';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { FormsModule } from '@angular/forms';

const STATUS_COLOR: Record<string, StatusColor> = {
  PENDING_APPROVAL: 'info', APPROVED: 'info', ON_HOLD: 'warning',
  SCHEDULED: 'info', EXECUTING: 'warning', RECONCILIATION_PENDING: 'warning',
  RETRY_SCHEDULED: 'warning', SUCCEEDED: 'success', FAILED: 'error',
  EXPIRED: 'neutral', CANCELLED: 'neutral', SUPERSEDED: 'neutral',
};

const OUTCOME_COLOR: Record<string, StatusColor> = {
  SUCCESS: 'success', RETRY: 'warning', FAILURE: 'error', INDETERMINATE: 'warning',
};

@Component({
  selector: 'dp-action-detail-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule, StatusBadgeComponent, FormsModule],
  template: `
    @if (actionQuery.isPending()) {
      <div class="flex h-32 items-center justify-center">
        <span class="dp-spinner inline-block h-6 w-6 rounded-full border-2 border-[var(--border-default)]"
              style="border-top-color: var(--accent-primary)"></span>
      </div>
    } @else if (action(); as a) {
      <div class="flex flex-col gap-4 p-4">
        <!-- Header -->
        <div class="flex items-center justify-between">
          <div>
            <h3 class="text-sm font-semibold text-[var(--text-primary)]">{{ a.offerName }}</h3>
            <span class="text-[11px] text-[var(--text-secondary)]">{{ a.sku }}</span>
          </div>
          <div class="flex items-center gap-2">
            <dp-status-badge [label]="statusLabel()" [color]="statusColor()" />
            <button (click)="openFullPage()"
                    class="cursor-pointer rounded-[var(--radius-sm)] p-1 text-[var(--text-tertiary)] hover:text-[var(--text-primary)]"
                    [attr.aria-label]="'execution.context_menu.open_new_tab' | translate">
              <lucide-icon [img]="ExternalLinkIcon" [size]="14" />
            </button>
          </div>
        </div>

        <!-- Info -->
        <div class="grid grid-cols-2 gap-x-4 gap-y-2 text-[13px]">
          <div>
            <span class="text-[var(--text-tertiary)]">{{ 'execution.detail.target_price' | translate }}</span>
            <p class="font-mono font-medium text-[var(--text-primary)]">{{ formatPrice(a.targetPrice) }}</p>
          </div>
          <div>
            <span class="text-[var(--text-tertiary)]">{{ 'execution.detail.current_price' | translate }}</span>
            <p class="font-mono font-medium text-[var(--text-primary)]">{{ formatPrice(a.currentPriceAtCreation) }}</p>
          </div>
          <div>
            <span class="text-[var(--text-tertiary)]">{{ 'execution.detail.connection' | translate }}</span>
            <p class="text-[var(--text-primary)]">{{ a.connectionName }}</p>
          </div>
          <div>
            <span class="text-[var(--text-tertiary)]">{{ 'execution.detail.attempts' | translate }}</span>
            <p class="font-mono text-[var(--text-primary)]">{{ a.attemptCount }}/{{ a.maxAttempts }}</p>
          </div>
        </div>

        <!-- Actions -->
        @if (rbac.canOperateActions()) {
          <div class="flex flex-wrap gap-2">
            @if (canApprove() && rbac.canApproveActions()) {
              <button (click)="approve()"
                      class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-3 py-1.5 text-[13px] font-medium text-white hover:bg-[var(--accent-primary-hover)]">
                {{ 'execution.detail.btn_approve' | translate }}
              </button>
            }
            @if (canHold()) {
              <button (click)="holdInline.set(true)"
                      class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1.5 text-[13px] text-[var(--text-primary)] hover:bg-[var(--bg-tertiary)]">
                {{ 'execution.detail.btn_hold' | translate }}
              </button>
            }
            @if (canResume()) {
              <button (click)="resume()"
                      class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-3 py-1.5 text-[13px] font-medium text-white hover:bg-[var(--accent-primary-hover)]">
                {{ 'execution.detail.btn_resume' | translate }}
              </button>
            }
            @if (canCancel()) {
              <button (click)="cancelInline.set(true)"
                      class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--status-error)] px-3 py-1.5 text-[13px] text-[var(--status-error)] hover:bg-[color-mix(in_srgb,var(--status-error)_8%,transparent)]">
                {{ 'execution.detail.btn_cancel' | translate }}
              </button>
            }
          </div>
        } @else {
          <p class="text-[13px] text-[var(--text-tertiary)]">{{ 'execution.detail.no_permission' | translate }}</p>
        }

        <!-- Inline hold form -->
        @if (holdInline()) {
          <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] p-3">
            <textarea [ngModel]="reasonText()" (ngModelChange)="reasonText.set($event)"
                      class="mb-2 w-full resize-y rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 py-1.5 text-[13px] text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                      [placeholder]="'execution.detail.hold_placeholder' | translate"
                      rows="2"></textarea>
            <div class="flex gap-2">
              <button (click)="submitHold()" [disabled]="reasonText().length < 5"
                      class="cursor-pointer rounded-[var(--radius-sm)] bg-[var(--accent-primary)] px-3 py-1 text-[13px] text-white disabled:opacity-50">
                {{ 'execution.detail.btn_hold' | translate }}
              </button>
              <button (click)="holdInline.set(false); reasonText.set('')"
                      class="cursor-pointer rounded-[var(--radius-sm)] px-3 py-1 text-[13px] text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]">
                {{ 'actions.cancel' | translate }}
              </button>
            </div>
          </div>
        }

        <!-- Inline cancel form -->
        @if (cancelInline()) {
          <div class="rounded-[var(--radius-md)] border border-[var(--status-error)] p-3">
            <textarea [ngModel]="reasonText()" (ngModelChange)="reasonText.set($event)"
                      class="mb-2 w-full resize-y rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 py-1.5 text-[13px] text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                      [placeholder]="'execution.detail.cancel_placeholder' | translate"
                      rows="2"></textarea>
            <div class="flex gap-2">
              <button (click)="submitCancel()" [disabled]="reasonText().length < 5"
                      class="cursor-pointer rounded-[var(--radius-sm)] bg-[var(--status-error)] px-3 py-1 text-[13px] text-white disabled:opacity-50">
                {{ 'execution.detail.btn_cancel' | translate }}
              </button>
              <button (click)="cancelInline.set(false); reasonText.set('')"
                      class="cursor-pointer rounded-[var(--radius-sm)] px-3 py-1 text-[13px] text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]">
                {{ 'actions.cancel' | translate }}
              </button>
            </div>
          </div>
        }

        <!-- Attempts compact -->
        @if (a.attempts.length > 0) {
          <div>
            <h4 class="mb-2 text-[11px] font-semibold uppercase text-[var(--text-tertiary)]">
              {{ 'execution.detail.attempts_title' | translate }}
            </h4>
            @for (att of a.attempts; track att.attemptNumber) {
              <div class="flex items-center gap-2 rounded-[var(--radius-sm)] px-2 py-1.5 text-[13px] hover:bg-[var(--bg-secondary)]"
                   (click)="toggleAttempt(att.attemptNumber)">
                <span class="font-mono text-[var(--text-tertiary)]">#{{ att.attemptNumber }}</span>
                <dp-status-badge [label]="outcomeLabel(att.outcome)" [color]="outcomeColor(att.outcome)" />
                <span class="flex-1 truncate text-[var(--text-secondary)]">{{ att.errorMessage ?? '—' }}</span>
                <lucide-icon [img]="expandedAttempt() === att.attemptNumber ? ChevronUpIcon : ChevronDownIcon" [size]="12" />
              </div>
              @if (expandedAttempt() === att.attemptNumber) {
                <div class="ml-6 mt-1 mb-2 text-[12px]">
                  <pre class="overflow-auto rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-2 font-mono text-[11px]">{{ att.providerRequest ?? '—' }}</pre>
                  <pre class="mt-1 overflow-auto rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-2 font-mono text-[11px]">{{ att.providerResponse ?? '—' }}</pre>
                </div>
              }
            }
          </div>
        }
      </div>
    }
  `,
})
export class ActionDetailPanelComponent {
  private readonly actionApi = inject(ActionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly panel = inject(DetailPanelService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  private readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  protected readonly ExternalLinkIcon = ExternalLink;
  protected readonly ChevronDownIcon = ChevronDown;
  protected readonly ChevronUpIcon = ChevronUp;

  readonly reasonText = signal('');
  readonly holdInline = signal(false);
  readonly cancelInline = signal(false);
  readonly expandedAttempt = signal<number | null>(null);

  private readonly actionId = computed(() => this.panel.entityId() ?? 0);

  readonly actionQuery = injectQuery(() => ({
    queryKey: ['action', this.actionId()],
    queryFn: () => lastValueFrom(
      this.actionApi.getAction(this.wsStore.currentWorkspaceId()!, this.actionId()),
    ),
    enabled: !!this.wsStore.currentWorkspaceId() && this.actionId() > 0,
    staleTime: 10_000,
    refetchInterval: 15_000,
  }));

  readonly action = computed(() => this.actionQuery.data() ?? null);
  readonly status = computed(() => this.action()?.status ?? '');
  readonly statusLabel = computed(() =>
    this.translate.instant(`grid.action_status.${this.status()}`),
  );
  readonly statusColor = computed(() => STATUS_COLOR[this.status()] ?? 'neutral');
  readonly canApprove = computed(() => this.status() === 'PENDING_APPROVAL');
  readonly canHold = computed(() => this.status() === 'APPROVED');
  readonly canResume = computed(() => this.status() === 'ON_HOLD');
  readonly canCancel = computed(() =>
    ['PENDING_APPROVAL', 'APPROVED', 'ON_HOLD', 'SCHEDULED', 'RETRY_SCHEDULED', 'RECONCILIATION_PENDING'].includes(this.status()),
  );

  private readonly approveMut = injectMutation(() => ({
    mutationFn: () => lastValueFrom(
      this.actionApi.approveAction(this.wsStore.currentWorkspaceId()!, this.actionId()),
    ),
    onSuccess: () => this.afterMutation('execution.detail.toast.approved'),
    onError: () => this.toast.error(this.translate.instant('execution.detail.action_error')),
  }));

  private readonly holdMut = injectMutation(() => ({
    mutationFn: (reason: string) => lastValueFrom(
      this.actionApi.holdAction(this.wsStore.currentWorkspaceId()!, this.actionId(), reason),
    ),
    onSuccess: () => { this.holdInline.set(false); this.afterMutation('execution.detail.toast.held'); },
    onError: () => this.toast.error(this.translate.instant('execution.detail.action_error')),
  }));

  private readonly resumeMut = injectMutation(() => ({
    mutationFn: () => lastValueFrom(
      this.actionApi.resumeAction(this.wsStore.currentWorkspaceId()!, this.actionId()),
    ),
    onSuccess: () => this.afterMutation('execution.detail.toast.resumed'),
    onError: () => this.toast.error(this.translate.instant('execution.detail.action_error')),
  }));

  private readonly cancelMut = injectMutation(() => ({
    mutationFn: (reason: string) => lastValueFrom(
      this.actionApi.cancelAction(this.wsStore.currentWorkspaceId()!, this.actionId(), reason),
    ),
    onSuccess: () => { this.cancelInline.set(false); this.afterMutation('execution.detail.toast.cancelled'); },
    onError: () => this.toast.error(this.translate.instant('execution.detail.action_error')),
  }));

  approve(): void { this.approveMut.mutate(undefined); }
  resume(): void { this.resumeMut.mutate(undefined); }
  submitHold(): void { this.holdMut.mutate(this.reasonText()); }
  submitCancel(): void { this.cancelMut.mutate(this.reasonText()); }

  openFullPage(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.panel.close();
    this.router.navigate(['/workspace', wsId, 'execution', 'actions', this.actionId()]);
  }

  toggleAttempt(n: number): void {
    this.expandedAttempt.set(this.expandedAttempt() === n ? null : n);
  }

  formatPrice(v: number | null): string { return formatMoney(v, 0); }
  outcomeLabel(o: string): string { return this.translate.instant(`execution.detail.outcome.${o}`); }
  outcomeColor(o: string): StatusColor { return OUTCOME_COLOR[o] ?? 'neutral'; }

  private afterMutation(msgKey: string): void {
    this.reasonText.set('');
    this.queryClient.invalidateQueries({ queryKey: ['action', this.actionId()] });
    this.queryClient.invalidateQueries({ queryKey: ['actions'] });
    this.toast.success(this.translate.instant(msgKey));
  }
}
