import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '@core/auth/auth.service';
import { StatusMessageComponent } from '@shared/layout/status-message.component';

const LAST_WORKSPACE_KEY = 'dp_last_workspace_id';

type CallbackState = 'loading' | 'error';

@Component({
  selector: 'dp-callback',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
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
  private readonly router = inject(Router);

  protected readonly state = signal<CallbackState>('loading');

  ngOnInit(): void {
    this.processCallback();
  }

  protected onRetryLogin(): void {
    this.authService.login();
  }

  private processCallback(): void {
    this.authService.checkSession().subscribe({
      next: (ok) => {
        if (!ok) {
          this.state.set('error');
          return;
        }
        this.routeAfterLogin();
      },
      error: () => this.state.set('error'),
    });
  }

  private routeAfterLogin(): void {
    const me = this.authService.user();
    if (!me) {
      this.state.set('error');
      return;
    }

    if (me.needsOnboarding) {
      this.router.navigate(['/onboarding']);
      return;
    }

    const memberships = me.memberships;

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
