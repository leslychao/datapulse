import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { AuthService } from './auth.service';

interface WorkspaceMembership {
  workspace_id: number;
}

interface UserMeResponse {
  needs_onboarding: boolean;
  memberships: WorkspaceMembership[];
}

const LAST_WORKSPACE_KEY = 'dp_last_workspace_id';

export const rootRedirectGuard: CanActivateFn = async () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const http = inject(HttpClient);

  if (!authService.isAuthenticated()) {
    authService.initLogin();
    return false;
  }

  const me = await firstValueFrom(http.get<UserMeResponse>('/api/users/me'));

  if (me.needs_onboarding) {
    return router.createUrlTree(['/onboarding']);
  }

  if (me.memberships.length === 0) {
    return router.createUrlTree(['/workspaces']);
  }

  if (me.memberships.length === 1) {
    return router.createUrlTree(['/workspace', me.memberships[0].workspace_id, 'grid']);
  }

  const lastId = localStorage.getItem(LAST_WORKSPACE_KEY);
  const lastMembership = lastId
    ? me.memberships.find((m) => String(m.workspace_id) === lastId)
    : null;

  if (lastMembership) {
    return router.createUrlTree(['/workspace', lastMembership.workspace_id, 'grid']);
  }

  return router.createUrlTree(['/workspaces']);
};
