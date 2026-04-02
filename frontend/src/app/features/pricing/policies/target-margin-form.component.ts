import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';

import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, ChevronDown, ChevronUp } from 'lucide-angular';

import { CommissionSource, RoundingDirection } from '@core/models';

@Component({
  selector: 'dp-target-margin-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, LucideAngularModule],
  templateUrl: './target-margin-form.component.html',
})
export class TargetMarginFormComponent {
  readonly icons = { ChevronDown, ChevronUp };

  readonly form = input.required<FormGroup>();
  readonly submitted = input(false);
  readonly showAdvancedCommission = signal(false);

  readonly commissionSources: { value: CommissionSource; labelKey: string }[] = [
    { value: 'AUTO', labelKey: 'pricing.policies.commission_source.AUTO' },
    { value: 'MANUAL', labelKey: 'pricing.policies.commission_source.MANUAL' },
    { value: 'AUTO_WITH_MANUAL_FALLBACK', labelKey: 'pricing.policies.commission_source.AUTO_WITH_MANUAL_FALLBACK' },
  ];

  readonly roundingDirections: { value: RoundingDirection; labelKey: string }[] = [
    { value: 'FLOOR', labelKey: 'pricing.policies.rounding.FLOOR' },
    { value: 'NEAREST', labelKey: 'pricing.policies.rounding.NEAREST' },
    { value: 'CEIL', labelKey: 'pricing.policies.rounding.CEIL' },
  ];

  get commissionSource(): CommissionSource {
    return this.form().get('commissionSource')!.value;
  }

  get logisticsSource(): CommissionSource {
    return this.form().get('logisticsSource')!.value;
  }

  toggleAdvancedCommission(): void {
    this.showAdvancedCommission.update((v) => !v);
  }

  hasError(path: string, error: string): boolean {
    const ctrl = this.form().get(path);
    return !!ctrl && ctrl.hasError(error) && (ctrl.touched || this.submitted());
  }

  isInvalid(path: string): boolean {
    const ctrl = this.form().get(path);
    return !!ctrl && ctrl.invalid && (ctrl.touched || this.submitted());
  }
}
