import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectMutation, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule, Check, X, Pause, Calculator, Coins, Zap, Lock, Unlock } from 'lucide-angular';
import { CdkOverlayOrigin, CdkConnectedOverlay, ConnectedPosition } from '@angular/cdk/overlay';

import { ActionApiService } from '@core/api/action-api.service';
import { BiddingApiService } from '@core/api/bidding-api.service';
import { OfferSummary, CreateManualBidLockRequest } from '@core/models';
import { RbacService } from '@core/auth/rbac.service';
import { GridStore } from '@shared/stores/grid.store';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { FormModalComponent } from '@shared/components/form-modal.component';
import { FormulaPanelComponent } from './formula-panel.component';
import { CostUpdatePanelComponent } from './cost-update-panel.component';

@Component({
  selector: 'dp-bulk-actions-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe, LucideAngularModule, ConfirmationModalComponent,
    FormModalComponent,
    CdkOverlayOrigin, CdkConnectedOverlay, FormulaPanelComponent,
    CostUpdatePanelComponent,
  ],
  template: `
    <div class="flex items-center gap-3 border-t border-[var(--border-default)]
                bg-[var(--bg-secondary)] px-4 py-2.5
                animate-[slideUp_150ms_ease]">
      <span class="text-[length:var(--text-sm)] font-medium text-[var(--text-primary)]">
        <span class="font-mono">{{ gridStore.selectedCount() }}</span> {{ 'grid.bulk.selected' | translate }}
      </span>

      <div class="flex items-center gap-2">
        <button
          (click)="showApproveModal.set(true)"
          class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                 bg-[var(--accent-primary)] px-3 py-1.5 text-[length:var(--text-sm)]
                 font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
        >
          <lucide-icon [img]="checkIcon" [size]="14" />
          {{ 'grid.bulk.approve_all' | translate }}
        </button>
        <button
          (click)="showRejectModal.set(true)"
          class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                 bg-[var(--status-error)] px-3 py-1.5 text-[length:var(--text-sm)]
                 font-medium text-white transition-colors hover:bg-[color-mix(in_srgb,var(--status-error)_85%,black)]"
        >
          <lucide-icon [img]="xIcon" [size]="14" />
          {{ 'grid.bulk.reject_all' | translate }}
        </button>
        <button
          (click)="showHoldModal.set(true)"
          class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                 border border-[var(--border-default)] px-3 py-1.5 text-[length:var(--text-sm)]
                 font-medium text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
        >
          <lucide-icon [img]="pauseIcon" [size]="14" />
          {{ 'grid.bulk.hold' | translate }}
        </button>

        <div class="mx-1 h-5 w-px bg-[var(--border-default)]"></div>

        <button
          cdkOverlayOrigin #formulaTrigger="cdkOverlayOrigin"
          (click)="showFormulaPanel.set(!showFormulaPanel())"
          class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                 border border-[var(--border-default)] px-3 py-1.5 text-[length:var(--text-sm)]
                 font-medium text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
        >
          <lucide-icon [img]="calculatorIcon" [size]="14" />
          {{ 'grid.bulk.change_price' | translate }}
        </button>

        <button
          cdkOverlayOrigin #costTrigger="cdkOverlayOrigin"
          (click)="showCostPanel.set(!showCostPanel())"
          class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                 border border-[var(--border-default)] px-3 py-1.5 text-[length:var(--text-sm)]
                 font-medium text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
        >
          <lucide-icon [img]="coinsIcon" [size]="14" />
          {{ 'grid.bulk.update_cost' | translate }}
        </button>

        @if (rbac.canWriteBidPolicies()) {
          <div class="mx-1 h-5 w-px bg-[var(--border-default)]"></div>
          <button
            (click)="showBidAssignModal.set(true)"
            class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                   border border-[var(--border-default)] px-3 py-1.5 text-[length:var(--text-sm)]
                   font-medium text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
          >
            <lucide-icon [img]="zapIcon" [size]="14" />
            {{ 'grid.bulk.assign_bid_strategy' | translate }}
          </button>
        }
        @if (rbac.canManageLocks()) {
          <button
            (click)="showBidLockModal.set(true)"
            class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                   border border-[var(--border-default)] px-3 py-1.5 text-[length:var(--text-sm)]
                   font-medium text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
          >
            <lucide-icon [img]="lockIcon" [size]="14" />
            {{ 'grid.bulk.lock_bid' | translate }}
          </button>
          <button
            (click)="showBidUnlockModal.set(true)"
            class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                   border border-[var(--border-default)] px-3 py-1.5 text-[length:var(--text-sm)]
                   font-medium text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
          >
            <lucide-icon [img]="unlockIcon" [size]="14" />
            {{ 'grid.bulk.unlock_bid' | translate }}
          </button>
        }
      </div>

      <ng-template cdkConnectedOverlay
                   [cdkConnectedOverlayOrigin]="formulaTrigger"
                   [cdkConnectedOverlayOpen]="showFormulaPanel()"
                   [cdkConnectedOverlayPositions]="formulaPanelPositions"
                   [cdkConnectedOverlayHasBackdrop]="true"
                   cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
                   (backdropClick)="showFormulaPanel.set(false)">
        <dp-formula-panel
          [offers]="selectedOffers()"
          (applied)="onFormulaApplied()"
          (close)="showFormulaPanel.set(false)"
        />
      </ng-template>

      <ng-template cdkConnectedOverlay
                   [cdkConnectedOverlayOrigin]="costTrigger"
                   [cdkConnectedOverlayOpen]="showCostPanel()"
                   [cdkConnectedOverlayPositions]="formulaPanelPositions"
                   [cdkConnectedOverlayHasBackdrop]="true"
                   cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
                   (backdropClick)="showCostPanel.set(false)">
        <dp-cost-update-panel
          [offers]="selectedOffers()"
          (applied)="onCostApplied()"
          (close)="showCostPanel.set(false)"
        />
      </ng-template>

      <div class="flex-1"></div>

      <button
        (click)="gridStore.clearSelection()"
        class="flex h-7 w-7 cursor-pointer items-center justify-center rounded-[var(--radius-sm)]
               text-[var(--text-tertiary)] transition-colors hover:bg-[var(--bg-tertiary)]
               hover:text-[var(--text-primary)]"
        [attr.aria-label]="'actions.close' | translate"
      >
        <lucide-icon [img]="xIcon" [size]="16" />
      </button>
    </div>

    <dp-confirmation-modal
      [open]="showApproveModal()"
      [title]="'grid.bulk.approve_confirm_title' | translate"
      [message]="approveMessage()"
      [confirmLabel]="approveLabel()"
      (confirmed)="onBulkApprove()"
      (cancelled)="showApproveModal.set(false)"
    />

    <dp-confirmation-modal
      [open]="showRejectModal()"
      [title]="'grid.bulk.reject_confirm_title' | translate"
      [message]="rejectMessage()"
      [confirmLabel]="rejectLabel()"
      [danger]="true"
      (confirmed)="onBulkReject()"
      (cancelled)="showRejectModal.set(false)"
    />

    <dp-confirmation-modal
      [open]="showHoldModal()"
      [title]="'grid.bulk.hold_confirm_title' | translate"
      [message]="holdMessage()"
      [confirmLabel]="'grid.bulk.hold' | translate"
      (confirmed)="onBulkHold()"
      (cancelled)="showHoldModal.set(false)"
    />

    <dp-form-modal
      [title]="'grid.bulk.assign_bid_strategy' | translate"
      [isOpen]="showBidAssignModal()"
      [submitLabel]="'grid.bulk.assign_confirm' | translate"
      [cancelLabel]="'actions.cancel' | translate"
      [isPending]="bulkBidAssignMutation.isPending()"
      [submitDisabled]="!bidAssignPolicyId"
      (submit)="onBulkBidAssign()"
      (close)="showBidAssignModal.set(false)"
    >
      <div class="space-y-3">
        <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
          {{ 'grid.bulk.bid_policy_label' | translate }}
        </label>
        <input
          type="number"
          [(ngModel)]="bidAssignPolicyId"
          placeholder="ID стратегии"
          class="h-9 w-full rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
        />
      </div>
    </dp-form-modal>

    <dp-form-modal
      [title]="'grid.bulk.lock_bid' | translate"
      [isOpen]="showBidLockModal()"
      [submitLabel]="'grid.bulk.lock_confirm' | translate"
      [cancelLabel]="'actions.cancel' | translate"
      [isPending]="bulkBidLockMutation.isPending()"
      (submit)="onBulkBidLock()"
      (close)="showBidLockModal.set(false)"
    >
      <div class="space-y-3">
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'grid.bulk.locked_bid_label' | translate }}
          </label>
          <input
            type="number"
            [(ngModel)]="bidLockBid"
            placeholder="1000"
            class="h-9 w-full rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
          />
        </div>
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'grid.bulk.lock_reason_label' | translate }}
          </label>
          <input
            type="text"
            [(ngModel)]="bidLockReason"
            class="h-9 w-full rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
          />
        </div>
      </div>
    </dp-form-modal>

    <dp-confirmation-modal
      [open]="showBidUnlockModal()"
      [title]="'grid.bulk.unlock_bid' | translate"
      [message]="unlockMessage()"
      [confirmLabel]="'grid.bulk.unlock_confirm' | translate"
      (confirmed)="onBulkBidUnlock()"
      (cancelled)="showBidUnlockModal.set(false)"
    />
  `,
  styles: [`
    @keyframes slideUp {
      from { transform: translateY(100%); opacity: 0; }
      to { transform: translateY(0); opacity: 1; }
    }
  `],
})
export class BulkActionsBarComponent {

  readonly selectedOffers = input<OfferSummary[]>([]);

  protected readonly gridStore = inject(GridStore);
  private readonly actionApi = inject(ActionApiService);
  private readonly biddingApi = inject(BiddingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly queryClient = injectQueryClient();
  protected readonly rbac = inject(RbacService);

  readonly checkIcon = Check;
  readonly xIcon = X;
  readonly pauseIcon = Pause;
  readonly calculatorIcon = Calculator;
  readonly coinsIcon = Coins;
  readonly zapIcon = Zap;
  readonly lockIcon = Lock;
  readonly unlockIcon = Unlock;

  private readonly translate = inject(TranslateService);

  readonly showApproveModal = signal(false);
  readonly showRejectModal = signal(false);
  readonly showHoldModal = signal(false);
  readonly showFormulaPanel = signal(false);
  readonly showCostPanel = signal(false);
  readonly showBidAssignModal = signal(false);
  readonly showBidLockModal = signal(false);
  readonly showBidUnlockModal = signal(false);

  bidAssignPolicyId: number | null = null;
  bidLockBid: number | null = null;
  bidLockReason = '';

  readonly formulaPanelPositions: ConnectedPosition[] = [
    { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -8 },
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 8 },
  ];

  readonly bulkApproveMutation = injectMutation(() => ({
    mutationFn: (ids: number[]) =>
      lastValueFrom(this.actionApi.bulkApprove(this.wsStore.currentWorkspaceId()!, { actionIds: ids })),
    onSuccess: (res) => {
      this.showApproveModal.set(false);
      this.gridStore.clearSelection();
      const failed = res.skipped + res.errored;
      if (failed > 0) {
        this.toast.warning(this.translate.instant('grid.bulk.approve_partial', {
          succeeded: res.processed, total: res.processed + failed, failed,
        }));
      } else {
        this.toast.success(this.translate.instant('grid.bulk.approve_success', { count: res.processed }));
      }
      this.invalidateQueries();
    },
    onError: () => {
      this.showApproveModal.set(false);
      this.toast.error(this.translate.instant('grid.bulk.approve_error'));
    },
  }));

  readonly bulkRejectMutation = injectMutation(() => ({
    mutationFn: (ids: number[]) =>
      lastValueFrom(this.actionApi.bulkReject(this.wsStore.currentWorkspaceId()!, { actionIds: ids })),
    onSuccess: (res) => {
      this.showRejectModal.set(false);
      this.gridStore.clearSelection();
      const failed = res.skipped + res.errored;
      if (failed > 0) {
        this.toast.warning(this.translate.instant('grid.bulk.reject_partial', {
          succeeded: res.processed, total: res.processed + failed, failed,
        }));
      } else {
        this.toast.success(this.translate.instant('grid.bulk.reject_success', { count: res.processed }));
      }
      this.invalidateQueries();
    },
    onError: () => {
      this.showRejectModal.set(false);
      this.toast.error(this.translate.instant('grid.bulk.reject_error'));
    },
  }));

  protected approveMessage(): string {
    return this.translate.instant('grid.bulk.approve_message', { count: this.gridStore.selectedCount() });
  }

  protected approveLabel(): string {
    return this.translate.instant('grid.bulk.approve_label', { count: this.gridStore.selectedCount() });
  }

  protected rejectMessage(): string {
    return this.translate.instant('grid.bulk.reject_message', { count: this.gridStore.selectedCount() });
  }

  protected rejectLabel(): string {
    return this.translate.instant('grid.bulk.reject_label', { count: this.gridStore.selectedCount() });
  }

  protected holdMessage(): string {
    return this.translate.instant('grid.bulk.hold_message', { count: this.gridStore.selectedCount() });
  }

  onBulkApprove(): void {
    const ids = Array.from(this.gridStore.selectedOfferIds());
    this.bulkApproveMutation.mutate(ids);
  }

  onBulkReject(): void {
    const ids = Array.from(this.gridStore.selectedOfferIds());
    this.bulkRejectMutation.mutate(ids);
  }

  onBulkHold(): void {
    this.showHoldModal.set(false);
    this.toast.info(this.translate.instant('grid.bulk.hold_wip'));
  }

  onFormulaApplied(): void {
    this.showFormulaPanel.set(false);
    this.gridStore.clearSelection();
    this.toast.success(this.translate.instant('grid.formula.applied'));
  }

  onCostApplied(): void {
    this.showCostPanel.set(false);
    this.gridStore.clearSelection();
    this.invalidateQueries();
  }

  readonly bulkBidAssignMutation = injectMutation(() => ({
    mutationFn: (params: { policyId: number; offerIds: number[] }) =>
      lastValueFrom(
        this.biddingApi.bulkAssign(
          this.wsStore.currentWorkspaceId()!,
          params.policyId,
          params.offerIds,
          'PRODUCT',
        ),
      ),
    onSuccess: () => {
      this.showBidAssignModal.set(false);
      this.bidAssignPolicyId = null;
      this.gridStore.clearSelection();
      this.toast.success(this.translate.instant('grid.bulk.bid_assign_success'));
      this.invalidateQueries();
    },
    onError: () => {
      this.showBidAssignModal.set(false);
      this.toast.error(this.translate.instant('grid.bulk.bid_assign_error'));
    },
  }));

  readonly bulkBidLockMutation = injectMutation(() => ({
    mutationFn: (requests: CreateManualBidLockRequest[]) =>
      lastValueFrom(
        this.biddingApi.bulkCreateLocks(this.wsStore.currentWorkspaceId()!, requests),
      ),
    onSuccess: () => {
      this.showBidLockModal.set(false);
      this.bidLockBid = null;
      this.bidLockReason = '';
      this.gridStore.clearSelection();
      this.toast.success(this.translate.instant('grid.bulk.bid_lock_success'));
      this.invalidateQueries();
    },
    onError: () => {
      this.showBidLockModal.set(false);
      this.toast.error(this.translate.instant('grid.bulk.bid_lock_error'));
    },
  }));

  onBulkBidAssign(): void {
    if (!this.bidAssignPolicyId) return;
    const offerIds = Array.from(this.gridStore.selectedOfferIds());
    this.bulkBidAssignMutation.mutate({
      policyId: this.bidAssignPolicyId,
      offerIds,
    });
  }

  readonly bulkBidUnlockMutation = injectMutation(() => ({
    mutationFn: (lockIds: number[]) =>
      lastValueFrom(
        this.biddingApi.bulkRemoveLocks(this.wsStore.currentWorkspaceId()!, lockIds),
      ),
    onSuccess: () => {
      this.showBidUnlockModal.set(false);
      this.gridStore.clearSelection();
      this.toast.success(this.translate.instant('grid.bulk.bid_unlock_success'));
      this.invalidateQueries();
    },
    onError: () => {
      this.showBidUnlockModal.set(false);
      this.toast.error(this.translate.instant('grid.bulk.bid_unlock_error'));
    },
  }));

  protected unlockMessage(): string {
    return this.translate.instant('grid.bulk.unlock_message', { count: this.gridStore.selectedCount() });
  }

  onBulkBidUnlock(): void {
    const offerIds = Array.from(this.gridStore.selectedOfferIds());
    this.bulkBidUnlockMutation.mutate(offerIds);
  }

  onBulkBidLock(): void {
    const offerIds = Array.from(this.gridStore.selectedOfferIds());
    const requests: CreateManualBidLockRequest[] = offerIds.map((id) => {
      const req: CreateManualBidLockRequest = { marketplaceOfferId: id };
      if (this.bidLockBid !== null) req.lockedBid = this.bidLockBid;
      if (this.bidLockReason.trim()) req.reason = this.bidLockReason.trim();
      return req;
    });
    this.bulkBidLockMutation.mutate(requests);
  }

  private invalidateQueries(): void {
    this.queryClient.invalidateQueries({ queryKey: ['offers'] });
    this.queryClient.invalidateQueries({ queryKey: ['offer-detail'] });
    this.queryClient.invalidateQueries({ queryKey: ['action-history'] });
    this.queryClient.invalidateQueries({ queryKey: ['grid-kpi'] });
  }
}
