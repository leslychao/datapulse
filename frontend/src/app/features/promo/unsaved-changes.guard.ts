import { CanDeactivateFn } from '@angular/router';

import { PromoPolicyFormPageComponent } from './policies/promo-policy-form-page.component';

export const promoUnsavedChangesGuard: CanDeactivateFn<PromoPolicyFormPageComponent> = (
  component,
) => component.canDeactivate();
