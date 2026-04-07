import {
  ChangeDetectionStrategy,
  Component,
  computed,
  HostListener,
  inject,
  output,
  signal,
} from '@angular/core';
import { injectMutation, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule, Pencil, AlertTriangle, Filter, X } from 'lucide-angular';

import { OfferApiService } from '@core/api/offer-api.service';
import { BulkManualPreviewResponse } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { GridStore } from '@shared/stores/grid.store';
import { ToastService } from '@shared/shell/toast/toast.service';

@Component({
  selector: 'dp-draft-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, LucideAngularModule],
  template: `
    <div class="flex items-center gap-4 border-b border-[var(--accent-primary)] bg-[color-mix(in_srgb,var(--accent-primary)_8%,transparent)] px-4 py-2.5">
      <div class="flex items-center gap-2">
        <lucide-icon [img]="pencilIcon" [size]="14" class="text-[var(--accent-primary)]" />
        <span class="text-[length:var(--text-sm)] font-semibold text-[var(--text-primary)]">
          {{ 'draft.title' | translate }}: <span class="font-mono">{{ gridStore.draftCount() }}</span> {{ 'draft.changes' | translate }}
        </span>
      </div>

      @if (avgChange() !== null) {
        <span class="text-[length:var(--text-sm)]" [style.color]="avgChange()! >= 0 ? 'var(--finance-positive)' : 'var(--finance-negative)'">
          {{ 'draft.avg_change' | translate }}: <span class="font-mono">{{ formatPct(avgChange()!) }}</span>
        </span>
      }

      @if (minProjectedMargin() !== null) {
        <span
          class="text-[length:var(--text-sm)]"
          [style.color]="minProjectedMargin()! < 0 ? 'var(--finance-negative)' : 'var(--text-secondary)'"
        >
          {{ 'draft.min_projected_margin' | translate }}: <span class="font-mono">{{ formatAbsPct(minProjectedMargin()!) }}</span>
        </span>
      }

      @if (negativeMarginCount() > 0) {
        <span class="flex items-center gap-1 text-[length:var(--text-sm)] text-[var(--status-warning)]">
          <lucide-icon [img]="alertIcon" [size]="13" />
          {{ 'draft.negative_margin_warning' | translate:{ count: negativeMarginCount() } }}
        </span>
      }

      <div class="flex-1"></div>

      @if (gridStore.showDraftOnly()) {
        <button
          (click)="gridStore.toggleShowDraftOnly()"
          class="flex cursor-pointer items-center gap-1.5 rounded-full bg-[var(--accent-subtle)] px-3 py-1
                 text-[length:var(--text-xs)] font-medium text-[var(--accent-primary)] transition-colors
                 hover:bg-[color-mix(in_srgb,var(--accent-primary)_15%,transparent)]"
        >
          {{ 'draft.filter_pill_changes_only' | translate }}
          <lucide-icon [img]="xIcon" [size]="12" />
        </button>
      } @else {
        <button
          (click)="gridStore.toggleShowDraftOnly()"
          class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] px-3 py-1.5
                 text-[length:var(--text-sm)] text-[var(--text-secondary)] transition-colors
                 hover:bg-[color-mix(in_srgb,var(--accent-primary)_8%,transparent)] hover:text-[var(--text-primary)]"
        >
          <lucide-icon [img]="filterIcon" [size]="14" />
          {{ 'draft.show_diff' | translate }}
        </button>
      }

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
          [disabled]="gridStore.draftCount() === 0 || previewMutation.isPending()"
          class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5
                 text-[length:var(--text-sm)] font-medium text-white transition-colors
                 hover:bg-[var(--accent-primary-hover)]
                 disabled:cursor-not-allowed disabled:opacity-50"
        >
          @if (previewMutation.isPending()) {
            {{ 'draft.previewing' | translate }}
          } @else {
            {{ 'draft.apply' | translate }} (<span class="font-mono">{{ gridStore.draftCount() }}</span>)
          }
        </button>
      </div>
    </div>

    @if (previewData()) {
      <div class="fixed inset-0 z-[9000] flex items-center justify-center">
        <div class="absolute inset-0 bg-[var(--bg-overlay)]" (click)="closePreview()"></div>
        <div
          role="dialog"
          aria-modal="true"
          class="relative z-10 w-full max-w-lg rounded-[var(--radius-lg)] border border-[var(--border-default)]
                 bg-[var(--bg-primary)] p-6 shadow-[var(--shadow-md)] animate-[fadeIn_150ms_ease]"
        >
          <h3 class="text-base font-semibold text-[var(--text-primary)]">
            {{ 'draft.apply_title' | translate }}
          </h3>

          <p class="mt-3 text-[length:var(--text-sm)] font-medium text-[var(--text-primary)]">
            {{ 'draft.preview.will_change' | translate:{ count: previewData()!.summary.willChange } }}
          </p>

          <div class="mt-3 flex flex-col gap-1.5 text-[length:var(--text-sm)] text-[var(--text-secondary)]">
            <div class="flex justify-between">
              <span>{{ 'draft.preview.avg_change' | translate }}</span>
              <span
                class="font-mono font-medium"
                [style.color]="previewData()!.summary.avgChangePct >= 0 ? 'var(--finance-positive)' : 'var(--finance-negative)'"
              >
                {{ formatPct(previewData()!.summary.avgChangePct) }}
              </span>
            </div>
            @if (previewData()!.summary.minMarginAfter !== null) {
              <div class="flex justify-between">
                <span>{{ 'draft.preview.min_margin' | translate }}</span>
                <span class="font-mono font-medium">{{ formatAbsPct(previewData()!.summary.minMarginAfter!) }}</span>
              </div>
            }
            <div class="flex justify-between">
              <span>{{ 'draft.preview.max_change' | translate }}</span>
              <span class="font-mono font-medium">{{ formatPct(previewData()!.summary.maxChangePct) }}</span>
            </div>
          </div>

          @if (previewData()!.summary.willSkip > 0 || constrainedCount() > 0) {
            <div class="mt-4 flex flex-col gap-1.5 text-[length:var(--text-sm)]">
              @if (previewData()!.summary.willSkip > 0) {
                <div class="flex items-center gap-1.5 text-[var(--status-warning)]">
                  <lucide-icon [img]="alertIcon" [size]="14" />
                  <span>{{ 'draft.preview.skipped' | translate:{ count: previewData()!.summary.willSkip } }}</span>
                </div>
              }
              @if (constrainedCount() > 0) {
                <div class="flex items-start gap-1.5 text-[var(--status-warning)]">
                  <lucide-icon [img]="alertIcon" [size]="14" class="mt-0.5 shrink-0" />
                  <div>
                    <span>{{ 'draft.preview.constrained' | translate:{ count: constrainedCount() } }}</span>
                    <span class="block text-[var(--text-tertiary)]">{{ 'draft.preview.constrained_hint' | translate }}</span>
                  </div>
                </div>
              }
            </div>
          }

          <p class="mt-4 text-[length:var(--text-sm)] text-[var(--text-tertiary)]">
            {{ 'draft.preview.approved_note' | translate }}
          </p>

          <div class="mt-6 flex justify-end gap-3">
            <button
              (click)="closePreview()"
              class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm font-medium
                     text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              {{ 'common.cancel' | translate }}
            </button>
            <button
              (click)="onConfirmApply()"
              [disabled]="applyMutation.isPending() || previewData()!.summary.willChange === 0"
              class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2
                     text-sm font-medium text-white transition-colors
                     hover:bg-[var(--accent-primary-hover)]
                     disabled:cursor-not-allowed disabled:opacity-50"
            >
              @if (applyMutation.isPending()) {
                {{ 'draft.applying' | translate }}
              } @else {
                {{ 'draft.apply' | translate }} (<span class="font-mono">{{ previewData()!.summary.willChange }}</span>)
              }
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    @keyframes fadeIn {
      from { opacity: 0; transform: scale(0.97); }
      to { opacity: 1; transform: scale(1); }
    }
  `],
})
export class DraftBannerComponent {

  @HostListener('keydown.escape')
  protected onEscape(): void {
    if (this.previewData()) this.closePreview();
  }

  protected readonly gridStore = inject(GridStore);
  private readonly offerApi = inject(OfferApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly queryClient = injectQueryClient();
  private readonly translate = inject(TranslateService);

  readonly pencilIcon = Pencil;
  readonly alertIcon = AlertTriangle;
  readonly filterIcon = Filter;
  readonly xIcon = X;
  readonly previewData = signal<BulkManualPreviewResponse | null>(null);
  readonly applied = output<void>();

  protected readonly minProjectedMargin = computed(() => {
    const changes = this.gridStore.draftChanges();
    if (changes.size === 0) return null;
    let min: number | null = null;
    changes.forEach((c) => {
      if (c.costPrice && c.costPrice > 0 && c.newPrice > 0) {
        const margin = ((c.newPrice - c.costPrice) / c.newPrice) * 100;
        if (min === null || margin < min) min = margin;
      }
    });
    return min;
  });

  protected readonly negativeMarginCount = computed(() => {
    const changes = this.gridStore.draftChanges();
    let count = 0;
    changes.forEach((c) => {
      if (c.costPrice && c.costPrice > 0 && c.newPrice > 0) {
        const margin = ((c.newPrice - c.costPrice) / c.newPrice) * 100;
        if (margin < 0) count++;
      }
    });
    return count;
  });

  protected readonly constrainedCount = computed(() => {
    const data = this.previewData();
    if (!data) return 0;
    return data.offers.filter(
      o => o.result === 'CHANGE' && o.effectivePrice !== o.requestedPrice,
    ).length;
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

  readonly previewMutation = injectMutation(() => ({
    mutationFn: () => {
      const changes = this.buildChanges();
      return lastValueFrom(
        this.offerApi.bulkManualPreview(this.wsStore.currentWorkspaceId()!, { changes }),
      );
    },
    onSuccess: (res: BulkManualPreviewResponse) => {
      this.previewData.set(res);
    },
    onError: () => {
      this.toast.error(this.translate.instant('draft.preview_error'));
    },
  }));

  readonly applyMutation = injectMutation(() => ({
    mutationFn: () => {
      const changes = this.buildChanges();
      return lastValueFrom(
        this.offerApi.bulkManualApply(this.wsStore.currentWorkspaceId()!, { changes }),
      );
    },
    onSuccess: (res) => {
      this.previewData.set(null);
      this.gridStore.clearDraftChanges();
      this.gridStore.setDraftMode(false);
      if (res.errored > 0) {
        this.toast.warning(this.translate.instant('draft.partial_success', {
          succeeded: res.processed, total: res.processed + res.errored, failed: res.errored,
        }));
      } else {
        this.toast.success(this.translate.instant('draft.success', { count: res.processed }));
      }
      this.queryClient.invalidateQueries({ queryKey: ['offers'] });
      this.queryClient.invalidateQueries({ queryKey: ['grid-kpi'] });
      this.applied.emit();
    },
    onError: () => {
      this.previewData.set(null);
      this.toast.error(this.translate.instant('draft.apply_error'));
    },
  }));

  onApply(): void {
    this.previewMutation.mutate(undefined);
  }

  closePreview(): void {
    this.previewData.set(null);
  }

  onConfirmApply(): void {
    this.applyMutation.mutate(undefined);
  }

  protected formatPct(v: number): string {
    const abs = Math.abs(v);
    const fixed = abs.toFixed(1).replace('.', ',');
    if (v > 0) return `+${fixed}%`;
    if (v < 0) return `\u2212${fixed}%`;
    return `${fixed}%`;
  }

  protected formatAbsPct(v: number): string {
    return v.toFixed(1).replace('.', ',') + '%';
  }

  private buildChanges() {
    return Array.from(this.gridStore.draftChanges().values())
      .map(d => ({ marketplaceOfferId: d.offerId, targetPrice: d.newPrice }));
  }
}
