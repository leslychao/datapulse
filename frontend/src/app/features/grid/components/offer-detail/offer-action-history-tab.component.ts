import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { injectQuery, injectMutation, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';

import { OfferApiService } from '@core/api/offer-api.service';
import { ActionHistoryEntry } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';
import { MoneyDisplayComponent } from '@shared/components/money-display.component';
import { DateDisplayComponent } from '@shared/components/date-display.component';
import { ToastService } from '@shared/shell/toast/toast.service';

@Component({
  selector: 'dp-offer-action-history-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    StatusBadgeComponent,
    MoneyDisplayComponent,
    DateDisplayComponent,
  ],
  template: `
    <div class="p-4">
      @if (historyQuery.isPending()) {
        <div class="flex justify-center py-8">
          <span class="dp-spinner inline-block h-5 w-5 rounded-full border-2 border-[var(--border-default)]"
                style="border-top-color: var(--accent-primary)"></span>
        </div>
      } @else if (entries().length === 0) {
        <p class="py-8 text-center text-[length:var(--text-sm)] text-[var(--text-secondary)]">
          {{ 'detail.actions.empty' | translate }}
        </p>
      } @else {
        <div class="space-y-3">
          @for (entry of entries(); track entry.id) {
            <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] p-3">
              <div class="flex items-center justify-between">
                <dp-date-display [date]="entry.actionDate" [mode]="'timestamp'" />
                <dp-status-badge
                  [label]="'grid.action_status.' + entry.status | translate"
                  [color]="statusColor(entry.status)"
                />
              </div>

              <div class="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                @if (entry.targetPrice !== null) {
                  <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                    {{ 'detail.action_history.target_price' | translate }}
                  </span>
                  <dp-money-display [value]="entry.targetPrice" />
                }

                @if (entry.actualPrice !== null) {
                  <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                    {{ 'detail.action_history.actual_price' | translate }}
                  </span>
                  <dp-money-display [value]="entry.actualPrice" />
                }

                @if (entry.executionMode) {
                  <span class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                    {{ 'detail.action_history.mode' | translate }}
                  </span>
                  <span class="font-mono text-[length:var(--text-sm)] text-[var(--text-primary)]">
                    {{ entry.executionMode }}
                  </span>
                }
              </div>

              @if (entry.reason) {
                <p class="mt-1.5 text-[length:var(--text-sm)] text-[var(--text-secondary)]">
                  {{ entry.reason }}
                </p>
              }

              @if (entry.status === 'PENDING_APPROVAL') {
                <div class="mt-2 flex gap-2">
                  <button
                    (click)="approveAction(entry.id)"
                    class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-3 py-1
                           text-[length:var(--text-sm)] font-medium text-white transition-colors
                           hover:bg-[var(--accent-primary-hover)]"
                  >
                    {{ 'detail.actions.approve' | translate }}
                  </button>
                  <button
                    (click)="rejectAction(entry.id)"
                    class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--status-error)] px-3 py-1
                           text-[length:var(--text-sm)] font-medium text-white transition-colors
                           hover:bg-[color-mix(in_srgb,var(--status-error)_85%,black)]"
                  >
                    {{ 'detail.actions.reject' | translate }}
                  </button>
                </div>
              }

              @if (entry.status === 'ON_HOLD') {
                <div class="mt-2 flex gap-2">
                  <button
                    (click)="resumeAction(entry.id)"
                    class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-3 py-1
                           text-[length:var(--text-sm)] font-medium text-white transition-colors
                           hover:bg-[var(--accent-primary-hover)]"
                  >
                    {{ 'detail.actions.resume' | translate }}
                  </button>
                </div>
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
export class OfferActionHistoryTabComponent {
  readonly offerId = input.required<number>();

  private readonly offerApi = inject(OfferApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly queryClient = injectQueryClient();

  readonly page = signal(0);

  readonly historyQuery = injectQuery(() => ({
    queryKey: ['action-history', this.wsStore.currentWorkspaceId(), this.offerId(), this.page()],
    queryFn: () => lastValueFrom(
      this.offerApi.getActionHistory(this.wsStore.currentWorkspaceId()!, this.offerId(), this.page(), 20),
    ),
    enabled: !!this.wsStore.currentWorkspaceId() && !!this.offerId(),
  }));

  protected readonly entries = computed(() => this.historyQuery.data()?.content ?? []);
  protected readonly hasMore = computed(() => {
    const data = this.historyQuery.data();
    return data ? data.number < data.totalPages - 1 : false;
  });

  readonly approveMutation = injectMutation(() => ({
    mutationFn: (actionId: number) =>
      lastValueFrom(this.offerApi.approveAction(this.wsStore.currentWorkspaceId()!, actionId)),
    onSuccess: () => {
      this.toast.success('Действие одобрено');
      this.invalidateQueries();
    },
    onError: () => this.toast.error('Не удалось одобрить действие'),
  }));

  readonly rejectMutation = injectMutation(() => ({
    mutationFn: (actionId: number) =>
      lastValueFrom(this.offerApi.rejectAction(this.wsStore.currentWorkspaceId()!, actionId, '')),
    onSuccess: () => {
      this.toast.success('Действие отклонено');
      this.invalidateQueries();
    },
    onError: () => this.toast.error('Не удалось отклонить действие'),
  }));

  readonly resumeMutation = injectMutation(() => ({
    mutationFn: (actionId: number) =>
      lastValueFrom(this.offerApi.resumeAction(this.wsStore.currentWorkspaceId()!, actionId)),
    onSuccess: () => {
      this.toast.success('Действие возобновлено');
      this.invalidateQueries();
    },
    onError: () => this.toast.error('Не удалось возобновить действие'),
  }));

  loadMore(): void {
    this.page.update((p) => p + 1);
  }

  approveAction(actionId: number): void {
    this.approveMutation.mutate(actionId);
  }

  rejectAction(actionId: number): void {
    this.rejectMutation.mutate(actionId);
  }

  resumeAction(actionId: number): void {
    this.resumeMutation.mutate(actionId);
  }

  protected statusColor(status: string): StatusColor {
    if (status === 'SUCCEEDED') return 'success';
    if (status === 'FAILED') return 'error';
    if (status === 'ON_HOLD' || status === 'RETRY_SCHEDULED') return 'warning';
    if (status === 'PENDING_APPROVAL' || status === 'EXECUTING' || status === 'RECONCILIATION_PENDING') return 'info';
    return 'neutral';
  }

  private invalidateQueries(): void {
    this.queryClient.invalidateQueries({ queryKey: ['action-history'] });
    this.queryClient.invalidateQueries({ queryKey: ['offer-detail'] });
    this.queryClient.invalidateQueries({ queryKey: ['offers'] });
  }
}
