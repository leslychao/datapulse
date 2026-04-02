import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';

import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, Shield, CircleCheck } from 'lucide-angular';

@Component({
  selector: 'dp-guard-config-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, LucideAngularModule],
  templateUrl: './guard-config-form.component.html',
})
export class GuardConfigFormComponent {
  readonly icons = { Shield, CircleCheck };

  readonly form = input.required<FormGroup>();
  readonly submitted = input(false);

  hasError(path: string, error: string): boolean {
    const ctrl = this.form().get(path);
    return !!ctrl && ctrl.hasError(error) && (ctrl.touched || this.submitted());
  }

  isInvalid(path: string): boolean {
    const ctrl = this.form().get(path);
    return !!ctrl && ctrl.invalid && (ctrl.touched || this.submitted());
  }
}
