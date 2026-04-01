import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';

import { OfferApiService } from '@core/api/offer-api.service';
import { PriceJournalEntry } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { PriceChangeIndicatorComponent } from '@shared/components/price-change-indicator.component';
import { DateDisplayComponent } from '@shared/components/date-display.component';

@Component({
  selector: 'dp-offer-price-journal-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    StatusBadgeComponent,
    PriceChangeIndicatorComponent,
    DateDisplayComponent,
  ],
  template: `
    <div class="p-4">
      <!-- Filters -->
      <div class="mb-4 flex items-center gap-3">
        <select
          [value]="decisionFilter()"
          (change)="onDecisionFilterChange($event)"
          class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]
                 px-2.5 py-1.5 text-[length:var(--text-sm)] text-[var(--text-primary)] outline-none
                 focus:border-[var(--accent-primary)]"
        >
          <option value="">{{ 'detail.journal.all_types' | translate }}</option>
          <option value="CHANGE">{{ 'grid.decision.CHANGE' | translate }}</option>
          <option value="SKIP">{{ 'grid.decision.SKIP' | translate }}</option>
          <option value="HOLD">{{ 'grid.decision.HOLD' | translate }}</option>
        </select>
      </div>

      @if (journalQuery.isPending()) {
        <div class="flex justify-center py-8">
          <span class="dp-spinner inline-block h-5 w-5 rounded-full border-2 border-[var(--border-default)]"
                style="border-top-color: var(--accent-primary)"></span>
        </div>
      } @else if (entries().length === 0) {
        <p class="py-8 text-center text-[length:var(--text-sm)] text-[var(--text-secondary)]">
          {{ 'detail.journal.empty' | translate }}
        </p>
      } @else {
        <div class="space-y-3">
          @for (entry of entries(); track entry.id) {
            <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] p-3">
              <div class="mb-2">
                <dp-date-display [date]="entry.decisionDate" [mode]="'timestamp'" />
              </div>
              <div class="flex items-center gap-2">
                <dp-status-badge
                  [label]="'grid.decision.' + entry.decisionType | translate"
                  [color]="decisionColor(entry.decisionType)"
                />
                @if (entry.currentPrice !== null && entry.targetPrice !== null && entry.decisionType === 'CHANGE') {
                  <dp-price-change-indicator [oldPrice]="entry.currentPrice" [newPrice]="entry.targetPrice" />
                }
              </div>

              @if (entry.policyName) {
                <p class="mt-1.5 text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                  {{ entry.policyName }}
                  @if (entry.policyVersion) {
                    (v{{ entry.policyVersion }})
                  }
                </p>
              }

              @if (entry.actionStatus) {
                <div class="mt-1.5 flex items-center gap-2">
                  <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                    {{ 'detail.journal.action' | translate }}:
                  </span>
                  <dp-status-badge
                    [label]="'grid.action_status.' + entry.actionStatus | translate"
                    [color]="actionColor(entry.actionStatus)"
                  />
                </div>
              }

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
export class OfferPriceJournalTabComponent {
  readonly offerId = input.required<number>();

  private readonly offerApi = inject(OfferApiService);
  private readonly wsStore = inject(WorkspaceContextStore);

  readonly decisionFilter = signal('');
  readonly page = signal(0);

  readonly journalQuery = injectQuery(() => ({
    queryKey: ['price-journal', this.wsStore.currentWorkspaceId(), this.offerId(), this.decisionFilter(), this.page()],
    queryFn: () => lastValueFrom(
      this.offerApi.getPriceJournal(
        this.wsStore.currentWorkspaceId()!,
        this.offerId(),
        this.page(),
        20,
        this.decisionFilter() || undefined,
      ),
    ),
    enabled: !!this.wsStore.currentWorkspaceId() && !!this.offerId(),
  }));

  protected readonly entries = computed(() => this.journalQuery.data()?.content ?? []);
  protected readonly hasMore = computed(() => {
    const data = this.journalQuery.data();
    return data ? data.number < data.totalPages - 1 : false;
  });

  onDecisionFilterChange(event: Event): void {
    this.decisionFilter.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
  }

  loadMore(): void {
    this.page.update((p) => p + 1);
  }

  protected decisionColor(type: string): StatusColor {
    if (type === 'CHANGE') return 'info';
    if (type === 'HOLD') return 'warning';
    return 'neutral';
  }

  protected actionColor(status: string): StatusColor {
    if (status === 'SUCCEEDED') return 'success';
    if (status === 'FAILED') return 'error';
    if (status === 'ON_HOLD' || status === 'RETRY_SCHEDULED') return 'warning';
    if (status === 'PENDING_APPROVAL' || status === 'EXECUTING') return 'info';
    return 'neutral';
  }
}
