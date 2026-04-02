import { inject, Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

import { WorkspaceRole } from '@core/models';

@Pipe({
  name: 'dpRoleLabel',
  standalone: true,
  pure: true,
})
export class RoleLabelPipe implements PipeTransform {
  private readonly translate = inject(TranslateService);

  transform(role: WorkspaceRole): string {
    const key = `role.${role}`;
    const translated = this.translate.instant(key);
    return translated !== key ? translated : role;
  }
}
