import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { injectMutation, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, Check, X, Pause, Lock, Unlock } from 'lucide-angular';

import { OfferApiService } from '@core/api/offer-api.service';
import { GridStore } from '@shared/stores/grid.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';

@Component({
  selector: 'dp-bulk-actions-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule, ConfirmationModalComponent],
  template: `
    <div class="flex items-center gap-3 border-t border-[var(--border-default)]
                bg-[var(--bg-secondary)] px-4 py-2.5
                animate-[slideUp_150ms_ease]">
      <span class="text-[length:var(--text-sm)] font-medium text-[var(--text-primary)]">
        {{ gridStore.selectedCount() }} {{ 'grid.bulk.selected' | translate }}
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
      </div>

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
  `,
  styles: [`
    @keyframes slideUp {
      from { transform: translateY(100%); opacity: 0; }
      to { transform: translateY(0); opacity: 1; }
    }
  `],
})
export class BulkActionsBarComponent {
  protected readonly gridStore = inject(GridStore);
  private readonly offerApi = inject(OfferApiService);
  private readonly toast = inject(ToastService);
  private readonly queryClient = injectQueryClient();

  readonly checkIcon = Check;
  readonly xIcon = X;
  readonly pauseIcon = Pause;
  readonly lockIcon = Lock;
  readonly unlockIcon = Unlock;

  readonly showApproveModal = signal(false);
  readonly showRejectModal = signal(false);
  readonly showHoldModal = signal(false);

  readonly bulkApproveMutation = injectMutation(() => ({
    mutationFn: (ids: number[]) => lastValueFrom(this.offerApi.bulkApprove({ actionIds: ids })),
    onSuccess: (res) => {
      this.showApproveModal.set(false);
      this.gridStore.clearSelection();
      if (res.failed > 0) {
        this.toast.warning(`Одобрено ${res.succeeded} из ${res.succeeded + res.failed}. ${res.failed} не удалось.`);
      } else {
        this.toast.success(`${res.succeeded} действий одобрено`);
      }
      this.invalidateQueries();
    },
    onError: () => {
      this.showApproveModal.set(false);
      this.toast.error('Не удалось выполнить массовое одобрение');
    },
  }));

  readonly bulkRejectMutation = injectMutation(() => ({
    mutationFn: (ids: number[]) => lastValueFrom(this.offerApi.bulkReject({ actionIds: ids })),
    onSuccess: (res) => {
      this.showRejectModal.set(false);
      this.gridStore.clearSelection();
      if (res.failed > 0) {
        this.toast.warning(`Отклонено ${res.succeeded} из ${res.succeeded + res.failed}. ${res.failed} не удалось.`);
      } else {
        this.toast.success(`${res.succeeded} действий отклонено`);
      }
      this.invalidateQueries();
    },
    onError: () => {
      this.showRejectModal.set(false);
      this.toast.error('Не удалось выполнить массовое отклонение');
    },
  }));

  protected approveMessage(): string {
    return `Одобрить ${this.gridStore.selectedCount()} ценовых действий?\n\nЭто запустит исполнение для выбранных товаров.`;
  }

  protected approveLabel(): string {
    return `Одобрить (${this.gridStore.selectedCount()})`;
  }

  protected rejectMessage(): string {
    return `Отклонить ${this.gridStore.selectedCount()} ценовых действий?`;
  }

  protected rejectLabel(): string {
    return `Отклонить (${this.gridStore.selectedCount()})`;
  }

  protected holdMessage(): string {
    return `Приостановить ${this.gridStore.selectedCount()} действий?`;
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
    this.toast.info('Массовая приостановка — в разработке');
  }

  private invalidateQueries(): void {
    this.queryClient.invalidateQueries({ queryKey: ['offers'] });
    this.queryClient.invalidateQueries({ queryKey: ['offer-detail'] });
    this.queryClient.invalidateQueries({ queryKey: ['action-history'] });
    this.queryClient.invalidateQueries({ queryKey: ['grid-kpi'] });
  }
}
