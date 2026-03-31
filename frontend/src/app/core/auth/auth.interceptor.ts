import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

const WORKSPACE_HEADER_SKIP_PATHS = [
  '/api/users/me',
  '/api/tenants',
  '/api/workspaces',
  '/api/invitations/accept',
];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const workspaceStore = inject(WorkspaceContextStore, { optional: true });

  if (!req.url.startsWith('/api/')) {
    return next(req);
  }

  const authReq = req.clone({ setHeaders: buildHeaders(req.url, authService, workspaceStore) });

  return next(authReq).pipe(
    catchError((error) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }

      return from(authService.refreshAccessToken()).pipe(
        switchMap(() => {
          const retryReq = req.clone({
            setHeaders: buildHeaders(req.url, authService, workspaceStore),
          });
          return next(retryReq);
        }),
        catchError(() => {
          authService.initLogin();
          return throwError(() => error);
        }),
      );
    }),
  );
};

function buildHeaders(
  url: string,
  authService: AuthService,
  workspaceStore: InstanceType<typeof WorkspaceContextStore> | null,
): Record<string, string> {
  const headers: Record<string, string> = {};

  const token = authService.accessToken;
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const skipWorkspace = WORKSPACE_HEADER_SKIP_PATHS.some((path) => url.startsWith(path));
  if (!skipWorkspace && workspaceStore) {
    const workspaceId = workspaceStore.currentWorkspaceId();
    if (workspaceId) {
      headers['X-Workspace-Id'] = String(workspaceId);
    }
  }

  return headers;
}
