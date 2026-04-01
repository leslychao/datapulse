import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';

import { OfferApiService } from '@core/api/offer-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { MoneyDisplayComponent } from '@shared/components/money-display.component';
import { PercentDisplayComponent } from '@shared/components/percent-display.component';
import { DateDisplayComponent } from '@shared/components/date-display.component';

@Component({
  selector: 'dp-offer-promo-journal-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    StatusBadgeComponent,
    MoneyDisplayComponent,
    PercentDisplayComponent,
    DateDisplayComponent,
  ],
  template: `
    <div class="p-4">
      @if (promoQuery.isPending()) {
        <div class="flex justify-center py-8">
          <span class="dp-spinner inline-block h-5 w-5 rounded-full border-2 border-[var(--border-default)]"
                style="border-top-color: var(--accent-primary)"></span>
        </div>
      } @else if (entries().length === 0) {
        <p class="py-8 text-center text-[length:var(--text-sm)] text-[var(--text-secondary)]">
          {{ 'detail.promo.empty' | translate }}
        </p>
      } @else {
        <div class="space-y-4">
          @for (entry of entries(); track entry.id) {
            <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] p-3">
              <h5 class="text-[length:var(--text-sm)] font-semibold text-[var(--text-primary)]">
                {{ entry.promoName }}
              </h5>
              <p class="mt-0.5 text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                <dp-date-display [date]="entry.periodStart" [mode]="'absolute'" />
                –
                <dp-date-display [date]="entry.periodEnd" [mode]="'absolute'" />
                @if (entry.promoType) {
                  · {{ entry.promoType }}
                }
              </p>

              <div class="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                  {{ 'detail.promo.decision' | translate }}
                </span>
                <dp-status-badge
                  [label]="'detail.promo.decision_' + entry.participationDecision | translate"
                  [color]="participationColor(entry.participationDecision)"
                />

                @if (entry.requiredPrice !== null) {
                  <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                    {{ 'detail.promo.promo_price' | translate }}
                  </span>
                  <dp-money-display [value]="entry.requiredPrice" />
                }

                @if (entry.marginAtPromoPrice !== null) {
                  <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                    {{ 'detail.promo.margin_at_promo' | translate }}
                  </span>
                  <dp-percent-display [value]="entry.marginAtPromoPrice" />
                }

                @if (entry.marginDeltaPct !== null) {
                  <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                    {{ 'detail.promo.margin_delta' | translate }}
                  </span>
                  <dp-percent-display [value]="entry.marginDeltaPct" [sign]="true" />
                }

                @if (entry.actionStatus) {
                  <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                    {{ 'detail.promo.action' | translate }}
                  </span>
                  <dp-status-badge
                    [label]="'grid.action_status.' + entry.actionStatus | translate"
                    [color]="actionColor(entry.actionStatus)"
                  />
                }
              </div>

              @if (entry.explanationSummary) {
                <p class="mt-2 line-clamp-2 text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                  {{ entry.explanationSummary }}
                </p>
              }
            </div>
          }
        </div>

        @if (hasMore()) {
          <button
            (click)="loadMore()"
            class="mt-4 w-full cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)]
                   py-2 text-center text-[length:var(--text-sm)] font-medium text-[var(--text-secondary)]
                   transition-colors hover:bg-[var(--bg-tertiary)]"
          >
            {{ 'detail.journal.load_more' | translate }}
          </button>
        }
      }
    </div>
  `,
})
export class OfferPromoJournalTabComponent {
  readonly offerId = input.required<number>();

  private readonly offerApi = inject(OfferApiService);
  private readonly wsStore = inject(WorkspaceContextStore);

  readonly page = signal(0);

  readonly promoQuery = injectQuery(() => ({
    queryKey: ['promo-journal', this.wsStore.currentWorkspaceId(), this.offerId(), this.page()],
    queryFn: () => lastValueFrom(
      this.offerApi.getPromoJournal(this.wsStore.currentWorkspaceId()!, this.offerId(), this.page(), 20),
    ),
    enabled: !!this.wsStore.currentWorkspaceId() && !!this.offerId(),
  }));

  protected readonly entries = computed(() => this.promoQuery.data()?.content ?? []);
  protected readonly hasMore = computed(() => {
    const data = this.promoQuery.data();
    return data ? data.number < data.totalPages - 1 : false;
  });

  loadMore(): void {
    this.page.update((p) => p + 1);
  }

  protected participationColor(decision: string): StatusColor {
    if (decision === 'PARTICIPATE') return 'success';
    if (decision === 'DECLINE') return 'error';
    return 'warning';
  }

  protected actionColor(status: string): StatusColor {
    if (status === 'SUCCEEDED') return 'success';
    if (status === 'FAILED') return 'error';
    if (status === 'ON_HOLD') return 'warning';
    return 'neutral';
  }
}
