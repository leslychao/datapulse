import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from './auth.service';

const LAST_WORKSPACE_KEY = 'dp_last_workspace_id';

export const rootRedirectGuard: CanActivateFn = async () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    const ok = await firstValueFrom(authService.checkSession());
    if (!ok) {
      authService.login('/');
      return false;
    }
  }

  const me = authService.user();
  if (!me) {
    authService.login('/');
    return false;
  }

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
