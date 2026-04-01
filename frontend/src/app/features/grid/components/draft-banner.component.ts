import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { injectMutation, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule, Pencil } from 'lucide-angular';

import { OfferApiService } from '@core/api/offer-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { GridStore } from '@shared/stores/grid.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';

@Component({
  selector: 'dp-draft-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule, ConfirmationModalComponent],
  template: `
    <div class="flex items-center gap-4 border-b border-[var(--accent-primary)] bg-[color-mix(in_srgb,var(--accent-primary)_8%,transparent)] px-4 py-2.5">
      <div class="flex items-center gap-2">
        <lucide-icon [img]="pencilIcon" [size]="14" class="text-[var(--accent-primary)]" />
        <span class="text-[length:var(--text-sm)] font-semibold text-[var(--text-primary)]">
          {{ 'draft.title' | translate }}: {{ gridStore.draftCount() }} {{ 'draft.changes' | translate }}
        </span>
      </div>

      @if (avgChange() !== null) {
        <span class="text-[length:var(--text-sm)]" [style.color]="avgChange()! >= 0 ? 'var(--finance-positive)' : 'var(--finance-negative)'">
          {{ 'draft.avg_change' | translate }}: {{ formatPct(avgChange()!) }}
        </span>
      }

      <div class="flex-1"></div>

      <div class="flex items-center gap-2">
        <button
          (click)="gridStore.clearDraftChanges()"
          class="cursor-pointer rounded-[var(--radius-md)] px-3 py-1.5
                 text-[length:var(--text-sm)] font-medium text-[var(--status-error)]
                 transition-colors hover:bg-[color-mix(in_srgb,var(--status-error)_8%,transparent)]"
        >
          {{ 'draft.reset_all' | translate }}
        </button>
        <button
          (click)="onApply()"
          [disabled]="gridStore.draftCount() === 0 || applyMutation.isPending()"
          class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5
                 text-[length:var(--text-sm)] font-medium text-white transition-colors
                 hover:bg-[var(--accent-primary-hover)]
                 disabled:cursor-not-allowed disabled:opacity-50"
        >
          @if (applyMutation.isPending()) {
            {{ 'draft.applying' | translate }}
          } @else {
            {{ 'draft.apply' | translate }} ({{ gridStore.draftCount() }})
          }
        </button>
      </div>
    </div>

    <dp-confirmation-modal
      [open]="showPreview()"
      [title]="'draft.apply_title' | translate"
      [message]="previewMessage()"
      [confirmLabel]="applyLabel()"
      (confirmed)="onConfirmApply()"
      (cancelled)="showPreview.set(false)"
    />
  `,
})
export class DraftBannerComponent {
  protected readonly gridStore = inject(GridStore);
  private readonly offerApi = inject(OfferApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly queryClient = injectQueryClient();

  private readonly translate = inject(TranslateService);

  readonly pencilIcon = Pencil;
  readonly showPreview = signal(false);
  readonly applied = output<void>();

  protected readonly applyLabel = computed(() => {
    const label = this.translate.instant('draft.apply');
    return `${label} (${this.gridStore.draftCount()})`;
  });

  protected readonly avgChange = computed(() => {
    const changes = this.gridStore.draftChanges();
    if (changes.size === 0) return null;
    let total = 0;
    changes.forEach((c) => {
      if (c.originalPrice > 0) {
        total += ((c.newPrice - c.originalPrice) / c.originalPrice) * 100;
      }
    });
    return total / changes.size;
  });

  readonly applyMutation = injectMutation(() => ({
    mutationFn: () => {
      const items = Array.from(this.gridStore.draftChanges().values());
      return lastValueFrom(
        this.offerApi.bulkManualApply(this.wsStore.currentWorkspaceId()!, { items }),
      );
    },
    onSuccess: (res) => {
      this.showPreview.set(false);
      this.gridStore.clearDraftChanges();
      this.gridStore.setDraftMode(false);
      if (res.failed > 0) {
        this.toast.warning(`Применено ${res.succeeded} из ${res.succeeded + res.failed}. ${res.failed} ошибок.`);
      } else {
        this.toast.success(`${res.succeeded} ценовых действий создано`);
      }
      this.queryClient.invalidateQueries({ queryKey: ['offers'] });
      this.queryClient.invalidateQueries({ queryKey: ['grid-kpi'] });
      this.applied.emit();
    },
    onError: () => {
      this.showPreview.set(false);
      this.toast.error('Не удалось применить изменения');
    },
  }));

  onApply(): void {
    this.showPreview.set(true);
  }

  onConfirmApply(): void {
    this.applyMutation.mutate(undefined);
  }

  previewMessage(): string {
    const count = this.gridStore.draftCount();
    const avg = this.avgChange();
    const avgStr = avg !== null ? this.formatPct(avg) : '0%';
    return `${count} товаров будет изменено\n\nСреднее изменение: ${avgStr}\n\nДействия будут созданы со статусом APPROVED и отправлены на исполнение.`;
  }

  protected formatPct(v: number): string {
    const abs = Math.abs(v);
    const fixed = abs.toFixed(1).replace('.', ',');
    if (v > 0) return `+${fixed}%`;
    if (v < 0) return `\u2212${fixed}%`;
    return `${fixed}%`;
  }
}
