import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { TranslateService } from '@ngx-translate/core';

import { AuthService } from './auth.service';
import { ToastService } from '@shared/shell/toast/toast.service';

export const workspaceGuard: CanActivateFn = async (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);
  const translate = inject(TranslateService);

  const workspaceId = route.params['workspaceId'];
  const me = await firstValueFrom(authService.ensureUser());

  if (!me) {
    return router.createUrlTree(['/workspaces']);
  }

  const hasAccess = me.memberships.some((m) => String(m.workspaceId) === workspaceId);

  if (!hasAccess) {
    toast.error(translate.instant('workspace_selector.no_access'));
    return router.createUrlTree(['/workspaces']);
  }

  return true;
};
