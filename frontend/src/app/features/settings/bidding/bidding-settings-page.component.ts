import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { BiddingApiService } from '@core/api/bidding-api.service';
import { WorkspaceBiddingSettings } from '@core/models';
import { RbacService } from '@core/auth/rbac.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { SpinnerComponent } from '@shared/layout/spinner.component';

@Component({
  selector: 'dp-bidding-settings-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    SectionCardComponent,
    EmptyStateComponent,
    SpinnerComponent,
  ],
  template: `
    <div class="max-w-2xl">
      <div class="mb-6">
        <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">
          {{ 'settings.bidding.title' | translate }}
        </h1>
        <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">
          {{ 'settings.bidding.subtitle' | translate }}
        </p>
      </div>

      @if (settingsQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      } @else if (settingsQuery.isError()) {
        <dp-empty-state
          [message]="'common.load_error' | translate"
          [actionLabel]="'actions.retry' | translate"
          (action)="settingsQuery.refetch()"
        />
      }

      @if (settingsQuery.data(); as settings) {
        <div class="space-y-5">
          <dp-section-card [title]="'settings.bidding.global_switch' | translate">
            <div class="space-y-4">
              <div class="flex items-center justify-between">
                <div>
                  <p class="text-sm font-medium text-[var(--text-primary)]">
                    {{ 'settings.bidding.enabled_label' | translate }}
                  </p>
                  <p class="text-[var(--text-xs)] text-[var(--text-tertiary)]">
                    {{ 'settings.bidding.enabled_hint' | translate }}
                  </p>
                </div>
                @if (canEdit()) {
                  <button
                    type="button"
                    (click)="toggleEnabled()"
                    class="relative inline-flex h-6 w-11 items-center rounded-full transition-colors cursor-pointer"
                    [class]="formEnabled() ? 'bg-[var(--accent-primary)]' : 'bg-[var(--bg-tertiary)]'"
                  >
                    <span
                      class="inline-block h-4 w-4 transform rounded-full bg-white transition-transform"
                      [class]="formEnabled() ? 'translate-x-6' : 'translate-x-1'"
                    ></span>
                  </button>
                } @else {
                  <span class="text-sm" [class]="settings.biddingEnabled
                    ? 'text-[var(--status-success)]'
                    : 'text-[var(--status-error)]'">
                    {{ (settings.biddingEnabled ? 'common.enabled' : 'common.disabled') | translate }}
                  </span>
                }
              </div>
            </div>
          </dp-section-card>

          <dp-section-card [title]="'settings.bidding.limits_title' | translate">
            <div class="space-y-4">
              <div>
                <label class="mb-1 block text-sm text-[var(--text-secondary)]">
                  {{ 'settings.bidding.max_daily_spend_label' | translate }}
                </label>
                <p class="mb-1 text-[var(--text-xs)] text-[var(--text-tertiary)]">
                  {{ 'settings.bidding.max_daily_spend_hint' | translate }}
                </p>
                @if (canEdit()) {
                  <div class="flex items-center gap-2">
                    <input
                      type="number"
                      [ngModel]="formMaxDailySpend()"
                      (ngModelChange)="formMaxDailySpend.set($event)"
                      name="maxDailySpend"
                      min="0"
                      step="100"
                      placeholder="{{ 'settings.bidding.max_daily_spend_placeholder' | translate }}"
                      class="w-48 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                    />
                    <span class="text-sm text-[var(--text-tertiary)]">{{ 'common.currency_rub' | translate }}</span>
                  </div>
                } @else {
                  <p class="text-sm text-[var(--text-primary)]">
                    {{ settings.maxAggregateDailySpend != null
                      ? settings.maxAggregateDailySpend + ' ₽'
                      : ('settings.bidding.unlimited' | translate) }}
                  </p>
                }
              </div>

              <div>
                <label class="mb-1 block text-sm text-[var(--text-secondary)]">
                  {{ 'settings.bidding.min_interval_label' | translate }}
                </label>
                <p class="mb-1 text-[var(--text-xs)] text-[var(--text-tertiary)]">
                  {{ 'settings.bidding.min_interval_hint' | translate }}
                </p>
                @if (canEdit()) {
                  <div class="flex items-center gap-2">
                    <input
                      type="number"
                      [ngModel]="formMinInterval()"
                      (ngModelChange)="formMinInterval.set($event)"
                      name="minInterval"
                      min="1"
                      max="168"
                      class="w-24 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                    />
                    <span class="text-sm text-[var(--text-tertiary)]">{{ 'common.hours' | translate }}</span>
                  </div>
                } @else {
                  <p class="text-sm text-[var(--text-primary)]">
                    {{ settings.minDecisionIntervalHours }} {{ 'common.hours' | translate }}
                  </p>
                }
              </div>
            </div>
          </dp-section-card>

          @if (canEdit() && hasChanges()) {
            <div class="flex items-center gap-3">
              <button
                type="button"
                (click)="save()"
                [disabled]="updateMutation.isPending()"
                class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
              >
                @if (updateMutation.isPending()) {
                  {{ 'settings.bidding.saving' | translate }}
                } @else {
                  {{ 'actions.save' | translate }}
                }
              </button>
              <button
                type="button"
                (click)="resetForm()"
                class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-4 py-2 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
              >
                {{ 'actions.cancel' | translate }}
              </button>
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class BiddingSettingsPageComponent {

  private readonly biddingApi = inject(BiddingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly rbac = inject(RbacService);

  readonly formEnabled = signal(true);
  readonly formMaxDailySpend = signal<number | null>(null);
  readonly formMinInterval = signal(4);

  readonly canEdit = computed(() => this.rbac.isAdmin());

  readonly settingsQuery = injectQuery(() => ({
    queryKey: ['bidding-settings', this.wsStore.currentWorkspaceId()],
    queryFn: async () => {
      const settings = await lastValueFrom(
        this.biddingApi.getSettings(this.wsStore.currentWorkspaceId()!),
      );
      this.applyToForm(settings);
      return settings;
    },
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly hasChanges = computed(() => {
    const data = this.settingsQuery.data();
    if (!data) return false;
    return (
      this.formEnabled() !== data.biddingEnabled ||
      this.formMaxDailySpend() !== data.maxAggregateDailySpend ||
      this.formMinInterval() !== data.minDecisionIntervalHours
    );
  });

  readonly updateMutation = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(
        this.biddingApi.updateSettings(this.wsStore.currentWorkspaceId()!, {
          biddingEnabled: this.formEnabled(),
          maxAggregateDailySpend: this.formMaxDailySpend(),
          minDecisionIntervalHours: this.formMinInterval(),
        }),
      ),
    onSuccess: () => {
      this.settingsQuery.refetch();
      this.toast.success(
        this.translate.instant('settings.bidding.saved'),
      );
    },
    onError: () =>
      this.toast.error(
        this.translate.instant('settings.bidding.save_error'),
      ),
  }));

  toggleEnabled(): void {
    this.formEnabled.update((v) => !v);
  }

  save(): void {
    this.updateMutation.mutate(undefined);
  }

  resetForm(): void {
    const data = this.settingsQuery.data();
    if (data) this.applyToForm(data);
  }

  private applyToForm(settings: WorkspaceBiddingSettings): void {
    this.formEnabled.set(settings.biddingEnabled);
    this.formMaxDailySpend.set(settings.maxAggregateDailySpend);
    this.formMinInterval.set(settings.minDecisionIntervalHours);
  }
}
