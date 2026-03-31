import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

interface UserMeResponse {
  memberships: Array<{ workspace_id: number }>;
}

export const workspaceGuard: CanActivateFn = async (route) => {
  const http = inject(HttpClient);
  const router = inject(Router);

  const workspaceId = route.params['workspaceId'];
  const me = await firstValueFrom(http.get<UserMeResponse>('/api/users/me'));

  const hasAccess = me.memberships.some((m) => String(m.workspace_id) === workspaceId);

  if (!hasAccess) {
    return router.createUrlTree(['/workspaces']);
  }

  return true;
};
