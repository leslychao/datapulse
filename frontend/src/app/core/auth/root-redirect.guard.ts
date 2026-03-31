import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from './auth.service';
import { UserApiService } from '@core/api/user-api.service';

const LAST_WORKSPACE_KEY = 'dp_last_workspace_id';

export const rootRedirectGuard: CanActivateFn = async () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const userApi = inject(UserApiService);

  if (!authService.isAuthenticated()) {
    authService.initLogin();
    return false;
  }

  const me = await firstValueFrom(userApi.getMe());

  if (me.needsOnboarding) {
    return router.createUrlTree(['/onboarding']);
  }

  if (me.memberships.length === 0) {
    return router.createUrlTree(['/workspaces']);
  }

  if (me.memberships.length === 1) {
    return router.createUrlTree(['/workspace', me.memberships[0].workspaceId, 'grid']);
  }

  const lastId = localStorage.getItem(LAST_WORKSPACE_KEY);
  const lastMembership = lastId
    ? me.memberships.find((m) => String(m.workspaceId) === lastId)
    : null;

  if (lastMembership) {
    return router.createUrlTree(['/workspace', lastMembership.workspaceId, 'grid']);
  }

  return router.createUrlTree(['/workspaces']);
};
