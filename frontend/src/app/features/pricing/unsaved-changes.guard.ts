import { CanDeactivateFn } from '@angular/router';

import { PolicyFormPageComponent } from './policies/policy-form-page.component';

export const unsavedChangesGuard: CanDeactivateFn<PolicyFormPageComponent> = (
  component,
) => component.canDeactivate();
