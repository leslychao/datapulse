import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { TranslateService } from '@ngx-translate/core';

import { AuthService } from './auth.service';
import { ToastService } from '@shared/shell/toast/toast.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

const WORKSPACE_HEADER_SKIP_PATHS = [
  '/api/users/me',
  '/api/tenants',
  '/api/workspaces',
  '/api/invitations/accept',
];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const toast = inject(ToastService);
  const translate = inject(TranslateService);
  const workspaceStore = inject(WorkspaceContextStore, { optional: true });

  if (!req.url.startsWith('/api/')) {
    return next(req);
  }

  const headers: Record<string, string> = {};

  const urlPath = req.url.split('?')[0];
  const skipWorkspace = WORKSPACE_HEADER_SKIP_PATHS.some(
    (path) => urlPath === path
  );
  if (!skipWorkspace && workspaceStore) {
    const workspaceId = workspaceStore.currentWorkspaceId();
    if (workspaceId) {
      headers['X-Workspace-Id'] = String(workspaceId);
    }
  }

  const authReq = Object.keys(headers).length > 0
    ? req.clone({ setHeaders: headers })
    : req;

  return next(authReq).pipe(
    catchError((error) => {
      if (error instanceof HttpErrorResponse) {
        if (error.status === 401) {
          authService.login();
        } else if (error.status === 403) {
          toast.error(translate.instant('auth.forbidden'));
        }
      }
      return throwError(() => error);
    }),
  );
};
