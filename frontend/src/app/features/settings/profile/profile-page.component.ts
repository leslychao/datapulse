import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectMutation, injectQuery } from '@tanstack/angular-query-experimental';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { lastValueFrom } from 'rxjs';

import { UserApiService } from '@core/api/user-api.service';
import { AuthService } from '@core/auth/auth.service';
import { SectionCardComponent } from '@shared/components/section-card.component';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { ToastService } from '@shared/shell/toast/toast.service';

@Component({
  selector: 'dp-profile-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, TranslatePipe, SectionCardComponent, SpinnerComponent],
  template: `
    <div class="max-w-2xl">
      <div class="mb-6">
        <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">
          {{ 'settings.profile.title' | translate }}
        </h1>
        <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">
          {{ 'settings.profile.subtitle' | translate }}
        </p>
      </div>

      @if (profileQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      }

      @if (profileQuery.data(); as user) {
        <dp-section-card [title]="'settings.profile.card_title' | translate">
          <form (ngSubmit)="save()" class="space-y-4">
            <div>
              <label class="mb-1 block text-sm text-[var(--text-secondary)]">
                {{ 'settings.profile.email_label' | translate }}
              </label>
              <p class="text-sm text-[var(--text-primary)]">{{ user.email }}</p>
            </div>
            <div>
              <label class="mb-1 block text-sm text-[var(--text-secondary)]" for="profileName">
                {{ 'settings.profile.name_label' | translate }}
              </label>
              <input
                id="profileName"
                type="text"
                name="profileName"
                [(ngModel)]="displayName"
                required
                class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
              />
            </div>
            <button
              type="submit"
              [disabled]="
                !displayName.trim() || displayName.trim() === user.name || updateMutation.isPending()
              "
              class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              @if (updateMutation.isPending()) {
                {{ 'settings.profile.saving' | translate }}
              } @else {
                {{ 'actions.save' | translate }}
              }
            </button>
          </form>
        </dp-section-card>
      }
    </div>
  `,
})
export class ProfilePageComponent {
  private readonly userApi = inject(UserApiService);
  private readonly authService = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  displayName = '';

  readonly profileQuery = injectQuery(() => ({
    queryKey: ['user-profile', 'me'],
    queryFn: async () => {
      const u = await lastValueFrom(this.userApi.getMe());
      this.displayName = u.name;
      return u;
    },
  }));

  readonly updateMutation = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(this.userApi.updateMe({ name: this.displayName.trim() })),
    onSuccess: (updated) => {
      this.authService.applyCachedUser(updated);
      this.profileQuery.refetch();
      this.toast.success(this.translate.instant('settings.profile.updated'));
    },
    onError: () =>
      this.toast.error(this.translate.instant('settings.profile.update_error')),
  }));

  save(): void {
    if (!this.displayName.trim()) {
      return;
    }
    this.updateMutation.mutate(undefined);
  }
}
