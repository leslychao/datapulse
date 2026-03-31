import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { UserApiService } from '@core/api/user-api.service';

export const workspaceGuard: CanActivateFn = async (route) => {
  const userApi = inject(UserApiService);
  const router = inject(Router);

  const workspaceId = route.params['workspaceId'];
  const me = await firstValueFrom(userApi.getMe());

  const hasAccess = me.memberships.some((m) => String(m.workspaceId) === workspaceId);

  if (!hasAccess) {
    return router.createUrlTree(['/workspaces']);
  }

  return true;
};
