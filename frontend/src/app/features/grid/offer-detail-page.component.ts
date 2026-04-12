import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule, ArrowLeft, Lock, Unlock, Check, Ban, Pause, Play } from 'lucide-angular';

import { OfferApiService } from '@core/api/offer-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { MarketplaceBadgeComponent } from '@shared/components/marketplace-badge.component';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { MoneyDisplayComponent } from '@shared/components/money-display.component';
import { PercentDisplayComponent } from '@shared/components/percent-display.component';
import { OfferOverviewTabComponent } from './components/offer-detail/offer-overview-tab.component';
import { OfferPriceJournalTabComponent } from './components/offer-detail/offer-price-journal-tab.component';
import { OfferPromoJournalTabComponent } from './components/offer-detail/offer-promo-journal-tab.component';
import { OfferActionHistoryTabComponent } from './components/offer-detail/offer-action-history-tab.component';
import { OfferStockTabComponent } from './components/offer-detail/offer-stock-tab.component';
import { OfferBiddingTabComponent } from './components/offer-detail/offer-bidding-tab.component';

type DetailTab = 'overview' | 'price-journal' | 'promo-journal' | 'action-history' | 'stock' | 'bidding';

@Component({
  selector: 'dp-offer-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    LucideAngularModule,
    MarketplaceBadgeComponent,
    StatusBadgeComponent,
    MoneyDisplayComponent,
    PercentDisplayComponent,
    OfferOverviewTabComponent,
    OfferPriceJournalTabComponent,
    OfferPromoJournalTabComponent,
    OfferActionHistoryTabComponent,
    OfferStockTabComponent,
    OfferBiddingTabComponent,
  ],
  template: `
    <div class="flex flex-col gap-4 p-4">
      <button
        (click)="goBack()"
        class="flex w-fit cursor-pointer items-center gap-1.5 text-[length:var(--text-sm)]
               text-[var(--accent-primary)] transition-colors hover:text-[var(--accent-primary-hover)]"
      >
        <lucide-icon [img]="backIcon" [size]="14" />
        {{ 'grid.offer_detail.back' | translate }}
      </button>

      @if (offerQuery.isPending()) {
        <div class="dp-shimmer h-24 rounded-[var(--radius-md)]"></div>
        <div class="dp-shimmer h-20 rounded-[var(--radius-md)]"></div>
        <div class="dp-shimmer h-64 rounded-[var(--radius-md)]"></div>
      }

      @if (offerQuery.data(); as offer) {
        <!-- Summary header -->
        <div class="rounded-[var(--radius-md)] bg-[var(--bg-primary)] p-4 shadow-[var(--shadow-sm)]">
          <div class="mb-3 flex items-center gap-3">
            <h2 class="text-[length:var(--text-lg)] font-semibold text-[var(--text-primary)]">
              {{ offer.productName }}
            </h2>
            <dp-marketplace-badge [type]="offer.marketplaceType" />
            <dp-status-badge
              [label]="'grid.offer_status.' + offer.status | translate"
              [color]="offerStatusColor(offer.status)"
            />
          </div>
          <div class="grid grid-cols-2 gap-x-8 gap-y-1 text-[length:var(--text-sm)] lg:grid-cols-4">
            <div>
              <span class="text-[var(--text-secondary)]">{{ 'detail.overview.sku' | translate }}:</span>
              <span class="ml-1 font-mono">{{ offer.skuCode }}</span>
            </div>
            <div>
              <span class="text-[var(--text-secondary)]">{{ 'detail.overview.connection' | translate }}:</span>
              <span class="ml-1">{{ offer.connectionName }}</span>
            </div>
            @if (offer.category) {
              <div>
                <span class="text-[var(--text-secondary)]">{{ 'detail.overview.category' | translate }}:</span>
                <span class="ml-1">{{ offer.category }}</span>
              </div>
            }
            @if (offer.policyName) {
              <div>
                <span class="text-[var(--text-secondary)]">{{ 'detail.overview.policy_name' | translate }}:</span>
                <span class="ml-1">{{ offer.policyName }}</span>
              </div>
            }
          </div>
        </div>

        <!-- KPI strip -->
        <div class="grid grid-cols-5 gap-3">
          <div class="flex flex-col gap-0.5 rounded-[var(--radius-md)] bg-[var(--bg-primary)] p-3 shadow-[var(--shadow-sm)]">
            <span class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
              {{ 'detail.overview.current_price' | translate }}
            </span>
            <dp-money-display class="font-mono text-[length:var(--text-base)] font-semibold" [value]="offer.currentPrice" />
          </div>
          <div class="flex flex-col gap-0.5 rounded-[var(--radius-md)] bg-[var(--bg-primary)] p-3 shadow-[var(--shadow-sm)]">
            <span class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
              {{ 'detail.overview.cost_price' | translate }}
            </span>
            <dp-money-display class="font-mono text-[length:var(--text-base)] font-semibold" [value]="offer.costPrice" />
          </div>
          <div class="flex flex-col gap-0.5 rounded-[var(--radius-md)] bg-[var(--bg-primary)] p-3 shadow-[var(--shadow-sm)]">
            <span class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
              {{ 'detail.overview.margin' | translate }}
            </span>
            <dp-percent-display class="font-mono text-[length:var(--text-base)] font-semibold" [value]="offer.marginPct" />
          </div>
          <div class="flex flex-col gap-0.5 rounded-[var(--radius-md)] bg-[var(--bg-primary)] p-3 shadow-[var(--shadow-sm)]">
            <span class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
              {{ 'detail.overview.available' | translate }}
            </span>
            <span class="font-mono text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
              {{ offer.availableStock ?? '—' }}
            </span>
          </div>
          <div class="flex flex-col gap-0.5 rounded-[var(--radius-md)] bg-[var(--bg-primary)] p-3 shadow-[var(--shadow-sm)]">
            <span class="text-[length:var(--text-xs)] text-[var(--text-secondary)]">
              {{ 'detail.overview.velocity' | translate }}
            </span>
            <span class="font-mono text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
              @if (offer.velocity14d !== null) {
                {{ offer.velocity14d!.toFixed(1).replace('.', ',') }}
              } @else {
                —
              }
            </span>
          </div>
        </div>

        <!-- Actions bar -->
        <div class="flex items-center gap-2 rounded-[var(--radius-md)] bg-[var(--bg-primary)] px-4 py-2 shadow-[var(--shadow-sm)]">
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
              <lucide-icon [img]="rejectIcon" [size]="14" />
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

        <!-- Tabs -->
        <div class="rounded-[var(--radius-md)] bg-[var(--bg-primary)] shadow-[var(--shadow-sm)]">
          <div class="flex border-b border-[var(--border-default)]">
            @for (tab of tabs; track tab.key) {
              <button
                (click)="activeTab.set(tab.key)"
                class="cursor-pointer px-4 py-2.5 text-[length:var(--text-sm)] font-medium transition-colors"
                [class]="activeTab() === tab.key
                  ? 'border-b-2 border-[var(--accent-primary)] text-[var(--text-primary)]'
                  : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'"
              >
                {{ tab.label | translate }}
              </button>
            }
          </div>

          <div class="min-h-[400px]">
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
              @case ('bidding') {
                <dp-offer-bidding-tab
                  [offerId]="offer.offerId"
                  [bidPolicyName]="offer.bidPolicyName"
                  [bidStrategyType]="offer.bidStrategyType"
                  [currentBid]="offer.currentBid"
                />
              }
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class OfferDetailPageComponent {
  private readonly offerApi = inject(OfferApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly queryClient = injectQueryClient();
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly offerId = input.required<string>();

  readonly backIcon = ArrowLeft;
  readonly lockIcon = Lock;
  readonly unlockIcon = Unlock;
  readonly checkIcon = Check;
  readonly rejectIcon = Ban;
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
    { key: 'bidding', label: 'detail.tab.bidding' },
  ];

  private readonly numericOfferId = computed(() => Number(this.offerId()));

  readonly offerQuery = injectQuery(() => ({
    queryKey: ['offer-detail', this.wsStore.currentWorkspaceId(), this.numericOfferId()],
    queryFn: () => lastValueFrom(
      this.offerApi.getOffer(this.wsStore.currentWorkspaceId()!, this.numericOfferId()),
    ),
    enabled: !!this.wsStore.currentWorkspaceId() && !isNaN(this.numericOfferId()),
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

  goBack(): void {
    this.router.navigate([
      '/workspace', this.wsStore.currentWorkspaceId(), 'grid',
    ]);
  }

  toggleLock(offerId: number, currentlyLocked: boolean, currentPrice: number | null): void {
    this.lockMutation.mutate({ offerId, locked: currentlyLocked, currentPrice });
  }

  offerStatusColor(status: string): StatusColor {
    switch (status) {
      case 'ACTIVE': return 'success';
      case 'BLOCKED': return 'error';
      case 'INACTIVE': return 'neutral';
      default: return 'neutral';
    }
  }
}
