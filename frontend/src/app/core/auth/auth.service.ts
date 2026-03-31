import { Injectable, inject, signal } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { filter } from 'rxjs';

import { authConfig } from './auth.config';

const RETURN_URL_KEY = 'dp_return_url';
const LAST_WORKSPACE_KEY = 'dp_last_workspace_id';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauthService = inject(OAuthService);
  private readonly _isAuthenticated = signal(false);
  private refreshPromise: Promise<unknown> | null = null;
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;

  readonly isAuthenticated = this._isAuthenticated.asReadonly();

  constructor() {
    this.oauthService.configure(authConfig);
    this.oauthService.events.subscribe(() => {
      this._isAuthenticated.set(this.oauthService.hasValidAccessToken());
    });
  }

  async init(): Promise<boolean> {
    await this.oauthService.loadDiscoveryDocumentAndTryLogin();
    this.setupAutomaticRefresh();
    this._isAuthenticated.set(this.oauthService.hasValidAccessToken());
    return this.oauthService.hasValidAccessToken();
  }

  initLogin(returnUrl?: string): void {
    if (returnUrl) {
      sessionStorage.setItem(RETURN_URL_KEY, returnUrl);
    }
    this.oauthService.initCodeFlow();
  }

  async handleCallback(): Promise<boolean> {
    await this.oauthService.loadDiscoveryDocumentAndTryLogin();
    this._isAuthenticated.set(this.oauthService.hasValidAccessToken());
    return this.oauthService.hasValidAccessToken();
  }

  logout(): void {
    const lastWorkspaceId = localStorage.getItem(LAST_WORKSPACE_KEY);
    this.oauthService.logOut();
    if (lastWorkspaceId) {
      localStorage.setItem(LAST_WORKSPACE_KEY, lastWorkspaceId);
    }
  }

  get accessToken(): string {
    return this.oauthService.getAccessToken();
  }

  get userClaims(): Record<string, unknown> | null {
    return this.oauthService.getIdentityClaims() as Record<string, unknown> | null;
  }

  getReturnUrl(): string | null {
    const url = sessionStorage.getItem(RETURN_URL_KEY);
    sessionStorage.removeItem(RETURN_URL_KEY);
    return url;
  }

  refreshAccessToken(): Promise<unknown> {
    if (!this.refreshPromise) {
      this.refreshPromise = this.oauthService
        .silentRefresh()
        .finally(() => {
          this.refreshPromise = null;
        });
    }
    return this.refreshPromise;
  }

  private setupAutomaticRefresh(): void {
    this.oauthService.events
      .pipe(filter((e) => e.type === 'token_received' || e.type === 'silently_refreshed'))
      .subscribe(() => this.scheduleRefresh());

    if (this.oauthService.hasValidAccessToken()) {
      this.scheduleRefresh();
    }
  }

  private scheduleRefresh(): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
    }

    const expiration = this.oauthService.getAccessTokenExpiration();
    const delay = expiration - Date.now() - 60_000;

    if (delay > 0) {
      this.refreshTimer = setTimeout(() => {
        this.oauthService.silentRefresh().catch(() => {});
      }, delay);
    }
  }
}
