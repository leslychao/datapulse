import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule, X, PanelLeftClose, Lock, Unlock, Check, Ban, Pause, Play } from 'lucide-angular';

import { OfferApiService } from '@core/api/offer-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { DetailPanelService } from '@shared/services/detail-panel.service';
import { ToastService } from '@shared/shell/toast/toast.service';
import { MarketplaceBadgeComponent } from '@shared/components/marketplace-badge.component';
import { OfferOverviewTabComponent } from './offer-overview-tab.component';
import { OfferPriceJournalTabComponent } from './offer-price-journal-tab.component';
import { OfferPromoJournalTabComponent } from './offer-promo-journal-tab.component';
import { OfferActionHistoryTabComponent } from './offer-action-history-tab.component';
import { OfferStockTabComponent } from './offer-stock-tab.component';

export type DetailTab = 'overview' | 'price-journal' | 'promo-journal' | 'action-history' | 'stock';

@Component({
  selector: 'dp-offer-detail-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    LucideAngularModule,
    MarketplaceBadgeComponent,
    OfferOverviewTabComponent,
    OfferPriceJournalTabComponent,
    OfferPromoJournalTabComponent,
    OfferActionHistoryTabComponent,
    OfferStockTabComponent,
  ],
  template: `
    @if (offerQuery.data(); as offer) {
      <div class="flex h-full flex-col">
        <!-- Header -->
        <div class="shrink-0 border-b border-[var(--border-default)] px-4 py-3">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0 flex-1">
              <h3 class="truncate text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]"
                  [title]="offer.productName">
                {{ offer.productName }}
              </h3>
              <div class="mt-1 flex items-center gap-2">
                <dp-marketplace-badge [type]="offer.marketplaceType" />
                <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                  {{ offer.connectionName }}
                </span>
              </div>
            </div>
            <div class="flex items-center gap-1">
              <button class="flex h-7 w-7 items-center justify-center rounded-[var(--radius-sm)]
                             text-[var(--text-tertiary)] transition-colors
                             hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
                      (click)="panelService.close()"
                      [attr.aria-label]="'detail_panel.close' | translate">
                <lucide-icon [img]="closeIcon" [size]="16" />
              </button>
            </div>
          </div>
        </div>

        <!-- Actions bar -->
        <div class="flex shrink-0 items-center gap-2 border-b border-[var(--border-default)] px-4 py-2">
          @if (offer.lastActionStatus === 'PENDING_APPROVAL') {
            <button (click)="approveClicked.set(true)"
                    class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                           bg-[var(--accent-primary)] px-3 py-1.5 text-[length:var(--text-sm)]
                           font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]">
              <lucide-icon [img]="checkIcon" [size]="14" />
              {{ 'detail.actions.approve' | translate }}
            </button>
            <button (click)="rejectClicked.set(true)"
                    class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                           bg-[var(--status-error)] px-3 py-1.5 text-[length:var(--text-sm)]
                           font-medium text-white transition-colors hover:bg-[color-mix(in_srgb,var(--status-error)_85%,black)]">
              <lucide-icon [img]="xIcon" [size]="14" />
              {{ 'detail.actions.reject' | translate }}
            </button>
          }
          @if (offer.lastActionStatus === 'APPROVED') {
            <button (click)="holdClicked.set(true)"
                    class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                           border border-[var(--border-default)] px-3 py-1.5 text-[length:var(--text-sm)]
                           font-medium text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)]">
              <lucide-icon [img]="pauseIcon" [size]="14" />
              {{ 'detail.actions.hold' | translate }}
            </button>
          }
          @if (offer.lastActionStatus === 'ON_HOLD') {
            <button (click)="resumeClicked.set(true)"
                    class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                           bg-[var(--accent-primary)] px-3 py-1.5 text-[length:var(--text-sm)]
                           font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]">
              <lucide-icon [img]="playIcon" [size]="14" />
              {{ 'detail.actions.resume' | translate }}
            </button>
          }

          <div class="flex-1"></div>

          @if (offer.manualLock) {
            <button (click)="toggleLock(offer.offerId, true, offer.currentPrice)"
                    [disabled]="lockMutation.isPending()"
                    class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                           border border-[var(--border-default)] px-3 py-1.5 text-[length:var(--text-sm)]
                           font-medium text-[var(--text-primary)] transition-colors
                           hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-50">
              <lucide-icon [img]="unlockIcon" [size]="14" />
              {{ 'detail.actions.unlock' | translate }}
            </button>
          } @else {
            <button (click)="toggleLock(offer.offerId, false, offer.currentPrice)"
                    [disabled]="lockMutation.isPending()"
                    class="inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)]
                           border border-[var(--border-default)] px-3 py-1.5 text-[length:var(--text-sm)]
                           font-medium text-[var(--text-primary)] transition-colors
                           hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-50">
              <lucide-icon [img]="lockIcon" [size]="14" />
              {{ 'detail.actions.lock' | translate }}
            </button>
          }
        </div>

        <!-- Tab strip -->
        <div class="flex shrink-0 border-b border-[var(--border-default)]">
          @for (tab of tabs; track tab.key) {
            <button
              (click)="activeTab.set(tab.key)"
              class="cursor-pointer px-3 py-2 text-[length:var(--text-sm)] font-medium transition-colors"
              [class]="activeTab() === tab.key
                ? 'border-b-2 border-[var(--accent-primary)] text-[var(--text-primary)]'
                : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'"
            >
              {{ tab.label | translate }}
            </button>
          }
        </div>

        <!-- Tab content -->
        <div class="flex-1 overflow-auto">
          @switch (activeTab()) {
            @case ('overview') {
              <dp-offer-overview-tab [offer]="offer" />
            }
            @case ('price-journal') {
              <dp-offer-price-journal-tab [offerId]="offer.offerId" />
            }
            @case ('promo-journal') {
              <dp-offer-promo-journal-tab [offerId]="offer.offerId" />
            }
            @case ('action-history') {
              <dp-offer-action-history-tab [offerId]="offer.offerId" />
            }
            @case ('stock') {
              <dp-offer-stock-tab [offer]="offer" />
            }
          }
        </div>
      </div>
    } @else if (offerQuery.isPending()) {
      <div class="flex h-full items-center justify-center">
        <span class="dp-spinner inline-block h-6 w-6 rounded-full border-2 border-[var(--border-default)]"
              style="border-top-color: var(--accent-primary)"></span>
      </div>
    }
  `,
})
export class OfferDetailPanelComponent {
  protected readonly panelService = inject(DetailPanelService);
  private readonly offerApi = inject(OfferApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly queryClient = injectQueryClient();
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly closeIcon = X;
  readonly collapseIcon = PanelLeftClose;
  readonly lockIcon = Lock;
  readonly unlockIcon = Unlock;
  readonly checkIcon = Check;
  readonly xIcon = Ban;
  readonly pauseIcon = Pause;
  readonly playIcon = Play;

  readonly activeTab = signal<DetailTab>('overview');

  readonly approveClicked = signal(false);
  readonly rejectClicked = signal(false);
  readonly holdClicked = signal(false);
  readonly resumeClicked = signal(false);

  readonly tabs: { key: DetailTab; label: string }[] = [
    { key: 'overview', label: 'detail.tab.overview' },
    { key: 'price-journal', label: 'detail.tab.price_journal' },
    { key: 'promo-journal', label: 'detail.tab.promo_journal' },
    { key: 'action-history', label: 'detail.tab.action_history' },
    { key: 'stock', label: 'detail.tab.stock' },
  ];

  readonly offerQuery = injectQuery(() => ({
    queryKey: ['offer-detail', this.wsStore.currentWorkspaceId(), this.panelService.entityId()],
    queryFn: () => lastValueFrom(
      this.offerApi.getOffer(this.wsStore.currentWorkspaceId()!, this.panelService.entityId()!),
    ),
    enabled: this.panelService.entityType() === 'offer'
      && !!this.panelService.entityId()
      && !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly lockMutation = injectMutation(() => ({
    mutationFn: (params: { offerId: number; locked: boolean; currentPrice: number | null }) => {
      const wsId = this.wsStore.currentWorkspaceId()!;
      if (params.locked) {
        return lastValueFrom(this.offerApi.unlockOffer(wsId, params.offerId));
      }
      return lastValueFrom(
        this.offerApi.lockOffer(wsId, params.offerId, {
          lockedPrice: params.currentPrice ?? 0,
        }),
      );
    },
    onSuccess: () => {
      this.offerQuery.refetch();
      this.queryClient.invalidateQueries({ queryKey: ['offers'] });
    },
    onError: () => this.toast.error(this.translate.instant('grid.lock_toggle_error')),
  }));

  toggleLock(offerId: number, currentlyLocked: boolean, currentPrice: number | null): void {
    this.lockMutation.mutate({ offerId, locked: currentlyLocked, currentPrice });
  }
}
