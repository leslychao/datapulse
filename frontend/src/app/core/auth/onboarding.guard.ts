import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from './auth.service';

export const onboardingGuard: CanActivateFn = async () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const me = await firstValueFrom(authService.ensureUser());

  if (!me || (!me.needsOnboarding && me.memberships.length > 0)) {
    return router.createUrlTree(['/workspaces']);
  }

  return true;
};
