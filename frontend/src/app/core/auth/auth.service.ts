import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal, computed } from '@angular/core';
import { Observable, of, catchError, tap, map } from 'rxjs';

import { environment } from '@env';
import { UserProfile } from '@core/models';

const LAST_WORKSPACE_KEY = 'dp_last_workspace_id';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly _user = signal<UserProfile | null>(null);
  private readonly _sessionChecked = signal(false);

  readonly user = this._user.asReadonly();
  readonly sessionChecked = this._sessionChecked.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);

  checkSession(): Observable<boolean> {
    return this.http.get<UserProfile>(`${environment.apiUrl}/users/me`).pipe(
      tap((user) => {
        this._user.set(user);
        this._sessionChecked.set(true);
      }),
      map(() => true),
      catchError(() => {
        this._user.set(null);
        this._sessionChecked.set(true);
        return of(false);
      }),
    );
  }

  /**
   * Returns cached profile or fetches it once.
   * Safe to call from guards that run after authGuard — profile is already in memory.
   */
  ensureUser(): Observable<UserProfile | null> {
    const cached = this._user();
    if (cached) {
      return of(cached);
    }
    return this.checkSession().pipe(map(() => this._user()));
  }

  login(returnUrl?: string): void {
    const rd =
      returnUrl != null && returnUrl.length > 0
        ? returnUrl
        : `${window.location.pathname}${window.location.search}`;
    window.location.href = `${environment.oauth2.loginUrl}?rd=${encodeURIComponent(rd)}`;
  }

  logout(): void {
    const lastWorkspaceId = localStorage.getItem(LAST_WORKSPACE_KEY);
    window.location.href = `${environment.oauth2.logoutUrl}?rd=/`;
    if (lastWorkspaceId) {
      localStorage.setItem(LAST_WORKSPACE_KEY, lastWorkspaceId);
    }
  }

  /** Updates in-memory user after profile changes (e.g. PUT /users/me). */
  applyCachedUser(profile: UserProfile): void {
    this._user.set(profile);
  }
}
