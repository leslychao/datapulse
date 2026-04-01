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

  login(returnUrl?: string): void {
    const rd = returnUrl ?? window.location.pathname;
    window.location.href = `${environment.oauth2.loginUrl}?rd=${encodeURIComponent(rd)}`;
  }

  logout(): void {
    const lastWorkspaceId = localStorage.getItem(LAST_WORKSPACE_KEY);
    window.location.href = `${environment.oauth2.logoutUrl}?rd=/`;
    if (lastWorkspaceId) {
      localStorage.setItem(LAST_WORKSPACE_KEY, lastWorkspaceId);
    }
  }
}
