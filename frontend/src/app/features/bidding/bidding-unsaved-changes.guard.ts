import { CanDeactivateFn } from '@angular/router';

import { BidPolicyFormPageComponent } from './strategies/bid-policy-form-page.component';

export const biddingUnsavedChangesGuard: CanDeactivateFn<BidPolicyFormPageComponent> = (
  component,
) => component.canDeactivate();
