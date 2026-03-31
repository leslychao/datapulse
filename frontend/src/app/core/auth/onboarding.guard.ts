import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

interface UserMeResponse {
  memberships: Array<{ workspace_id: number }>;
}

export const onboardingGuard: CanActivateFn = async () => {
  const http = inject(HttpClient);
  const router = inject(Router);

  const me = await firstValueFrom(http.get<UserMeResponse>('/api/users/me'));

  if (me.memberships.length > 0) {
    return router.createUrlTree(['/workspaces']);
  }

  return true;
};
