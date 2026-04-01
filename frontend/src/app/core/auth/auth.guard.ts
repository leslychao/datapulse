import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);

  if (authService.isAuthenticated()) {
    return true;
  }

  return authService.checkSession().pipe(
    map((ok) => {
      if (ok) return true;
      authService.login(state.url);
      return false;
    }),
  );
};
