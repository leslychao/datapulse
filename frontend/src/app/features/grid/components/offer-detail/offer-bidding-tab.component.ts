import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { BiddingApiService } from '@core/api/bidding-api.service';
import { BidDecisionSummary } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { formatMoney, formatDateTime } from '@shared/utils/format.utils';

const DECISION_COLOR: Record<string, StatusColor> = {
  BID_UP: 'success',
  BID_DOWN: 'error',
  HOLD: 'neutral',
  PAUSE: 'warning',
  RESUME: 'info',
  SET_MINIMUM: 'info',
  EMERGENCY_CUT: 'error',
};

const POLICY_STATUS_COLOR: Record<string, StatusColor> = {
  DRAFT: 'neutral',
  ACTIVE: 'success',
  PAUSED: 'warning',
  ARCHIVED: 'neutral',
};

@Component({
  selector: 'dp-offer-bidding-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    StatusBadgeComponent,
    EmptyStateComponent,
  ],
  template: `
    <div class="space-y-5 p-4">

      <!-- Последние решения -->
      <section>
        <h4 class="mb-2 text-[length:var(--text-base)] font-semibold text-[var(--text-primary)]">
          {{ 'bidding.detail.recent_decisions' | translate }}
        </h4>

        @if (decisionsQuery.isPending()) {
          <div class="dp-shimmer h-32 rounded-[var(--radius-md)]"></div>
        } @else if (decisions().length === 0) {
          <dp-empty-state [message]="'bidding.detail.no_decisions' | translate" />
        } @else {
          <div class="divide-y divide-[var(--border-subtle)] rounded-[var(--radius-md)] border border-[var(--border-default)]">
            @for (dec of decisions(); track dec.id) {
              <div class="flex items-center gap-3 px-4 py-2.5">
                <dp-status-badge
                  [label]="'bidding.decision.' + dec.decisionType | translate"
                  [color]="decisionColor(dec.decisionType)"
                />
                <span class="font-mono text-[length:var(--text-sm)] text-[var(--text-primary)]">
                  {{ formatBid(dec.currentBid) }} → {{ formatBid(dec.targetBid) }}
                </span>
                <span class="ml-auto text-[length:var(--text-xs)] text-[var(--text-tertiary)]">
                  {{ formatDate(dec.createdAt) }}
                </span>
              </div>
            }
          </div>
          <button
            (click)="navigateToAllDecisions()"
            class="mt-2 cursor-pointer text-[length:var(--text-sm)] text-[var(--accent-primary)] transition-colors hover:text-[var(--accent-primary-hover)]"
          >
            {{ 'bidding.detail.all_decisions' | translate }} →
          </button>
        }
      </section>
    </div>
  `,
})
export class OfferBiddingTabComponent {
  private readonly biddingApi = inject(BiddingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  readonly offerId = input.required<number>();

  readonly decisionsQuery = injectQuery(() => ({
    queryKey: [
      'offer-bid-decisions',
      this.wsStore.currentWorkspaceId(),
      this.offerId(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.biddingApi.listDecisions(
          this.wsStore.currentWorkspaceId()!,
          { marketplaceOfferId: this.offerId() },
          0,
          5,
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !!this.offerId(),
    staleTime: 30_000,
  }));

  readonly decisions = computed<BidDecisionSummary[]>(
    () => this.decisionsQuery.data()?.content ?? [],
  );

  decisionColor(type: string): StatusColor {
    return DECISION_COLOR[type] ?? 'neutral';
  }

  formatBid(value: number | null): string {
    if (value === null || value === undefined) return '—';
    return formatMoney(value / 100, 0);
  }

  formatDate(iso: string): string {
    return formatDateTime(iso, 'full');
  }

  navigateToAllDecisions(): void {
    this.router.navigate(
      ['/workspace', this.wsStore.currentWorkspaceId(), 'bidding', 'decisions'],
      { queryParams: { marketplaceOfferId: this.offerId() } },
    );
  }
}
