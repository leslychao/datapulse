import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { UserApiService } from '@core/api/user-api.service';

export const onboardingGuard: CanActivateFn = async () => {
  const userApi = inject(UserApiService);
  const router = inject(Router);

  const me = await firstValueFrom(userApi.getMe());

  if (me.memberships.length > 0) {
    return router.createUrlTree(['/workspaces']);
  }

  return true;
};
