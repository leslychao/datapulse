import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '@core/auth/auth.service';
import { UserApiService } from '@core/api/user-api.service';
import { UserProfile } from '@core/models';
import { StatusMessageComponent } from '@shared/layout/status-message.component';

const LAST_WORKSPACE_KEY = 'dp_last_workspace_id';

type CallbackState = 'loading' | 'error';

@Component({
  selector: 'dp-callback',
  standalone: true,
  imports: [StatusMessageComponent],
  template: `
    <div class="flex h-screen items-center justify-center bg-[var(--bg-primary)]">
      @switch (state()) {
        @case ('loading') {
          <dp-status-message
            icon="spinner"
            title="Выполняется вход..."
          />
        }
        @case ('error') {
          <dp-status-message
            icon="error"
            title="Не удалось выполнить вход"
            description="Попробуйте ещё раз."
            actionLabel="Войти заново"
            (actionClick)="onRetryLogin()"
          />
        }
      }
    </div>
  `,
})
export class CallbackComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly userApi = inject(UserApiService);
  private readonly router = inject(Router);

  protected readonly state = signal<CallbackState>('loading');

  ngOnInit(): void {
    this.processCallback();
  }

  protected onRetryLogin(): void {
    this.authService.initLogin();
  }

  private async processCallback(): Promise<void> {
    try {
      const authenticated = await this.authService.handleCallback();
      if (!authenticated) {
        this.state.set('error');
        return;
      }

      const profile = await this.fetchProfile();
      this.routeByProfile(profile);
    } catch {
      this.state.set('error');
    }
  }

  private fetchProfile(): Promise<UserProfile> {
    return new Promise((resolve, reject) => {
      this.userApi.getMe().subscribe({ next: resolve, error: reject });
    });
  }

  private routeByProfile(profile: UserProfile): void {
    if (profile.needsOnboarding) {
      this.router.navigate(['/onboarding']);
      return;
    }

    const memberships = profile.memberships;

    if (memberships.length === 0) {
      this.router.navigate(['/workspaces']);
      return;
    }

    if (memberships.length === 1) {
      const wsId = memberships[0].workspaceId;
      localStorage.setItem(LAST_WORKSPACE_KEY, String(wsId));
      this.router.navigate(['/workspace', wsId, 'grid']);
      return;
    }

    const lastWsId = localStorage.getItem(LAST_WORKSPACE_KEY);
    if (lastWsId) {
      const match = memberships.find((m) => String(m.workspaceId) === lastWsId);
      if (match) {
        this.router.navigate(['/workspace', match.workspaceId, 'grid']);
        return;
      }
    }

    this.router.navigate(['/workspaces']);
  }
}
