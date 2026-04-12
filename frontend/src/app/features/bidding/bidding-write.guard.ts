import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';

import { RbacService } from '@core/auth/rbac.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';

export const biddingWriteGuard: CanActivateFn = () => {
  const rbac = inject(RbacService);
  const router = inject(Router);
  const toast = inject(ToastService);
  const translate = inject(TranslateService);
  const wsStore = inject(WorkspaceContextStore);

  if (rbac.canWritePolicies()) {
    return true;
  }

  toast.error(translate.instant('bidding.policies.no_write_access'));
  const wsId = wsStore.currentWorkspaceId();
  return router.createUrlTree(['/workspace', wsId, 'bidding', 'strategies']);
};
