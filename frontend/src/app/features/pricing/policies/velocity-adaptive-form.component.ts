import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';

import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, Info } from 'lucide-angular';

import { RoundingDirection } from '@core/models';

@Component({
  selector: 'dp-velocity-adaptive-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, LucideAngularModule],
  template: `
    <div [formGroup]="form()" class="space-y-4">

      <div class="grid grid-cols-2 gap-4">
        <!-- Deceleration threshold -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="decelerationThreshold" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.velocity.deceleration_threshold' | translate }}
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.velocity.deceleration_threshold' | translate }}</span>
            </span>
          </div>
          <div class="relative">
            <input id="decelerationThreshold" formControlName="decelerationThreshold" type="number"
              min="1" max="99" step="1"
              class="h-8 w-full rounded-[var(--radius-md)] border px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
              [class]="isInvalid('decelerationThreshold') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-xs)] text-[var(--text-tertiary)]">%</span>
          </div>
          @if (isInvalid('decelerationThreshold')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.velocity.deceleration_threshold_range' | translate }}</p>
          }
        </div>

        <!-- Acceleration threshold -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="accelerationThreshold" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.velocity.acceleration_threshold' | translate }}
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.velocity.acceleration_threshold' | translate }}</span>
            </span>
          </div>
          <div class="relative">
            <input id="accelerationThreshold" formControlName="accelerationThreshold" type="number"
              min="101" max="500" step="1"
              class="h-8 w-full rounded-[var(--radius-md)] border px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
              [class]="isInvalid('accelerationThreshold') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-xs)] text-[var(--text-tertiary)]">%</span>
          </div>
          @if (isInvalid('accelerationThreshold')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.velocity.acceleration_threshold_range' | translate }}</p>
          }
        </div>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <!-- Max deceleration discount -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="decelerationDiscountPct" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.velocity.deceleration_discount' | translate }}
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.velocity.deceleration_discount' | translate }}</span>
            </span>
          </div>
          <div class="relative">
            <input id="decelerationDiscountPct" formControlName="decelerationDiscountPct" type="number"
              min="1" max="30" step="1"
              class="h-8 w-full rounded-[var(--radius-md)] border px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
              [class]="isInvalid('decelerationDiscountPct') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-xs)] text-[var(--text-tertiary)]">%</span>
          </div>
          @if (isInvalid('decelerationDiscountPct')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.velocity.deceleration_discount_range' | translate }}</p>
          }
        </div>

        <!-- Max acceleration markup -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="accelerationMarkupPct" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.velocity.acceleration_markup' | translate }}
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.velocity.acceleration_markup' | translate }}</span>
            </span>
          </div>
          <div class="relative">
            <input id="accelerationMarkupPct" formControlName="accelerationMarkupPct" type="number"
              min="1" max="20" step="1"
              class="h-8 w-full rounded-[var(--radius-md)] border px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
              [class]="isInvalid('accelerationMarkupPct') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-xs)] text-[var(--text-tertiary)]">%</span>
          </div>
          @if (isInvalid('accelerationMarkupPct')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.velocity.acceleration_markup_range' | translate }}</p>
          }
        </div>
      </div>

      <!-- Min baseline sales -->
      <div class="max-w-xs">
        <div class="mb-1 flex items-center gap-1.5">
          <label for="minBaselineSales" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'pricing.form.velocity.min_baseline_sales' | translate }}
          </label>
          <span class="group relative inline-flex">
            <lucide-icon [name]="icons.Info" [size]="13"
              class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
            <span class="dp-tooltip">{{ 'pricing.form.tooltip.velocity.min_baseline_sales' | translate }}</span>
          </span>
        </div>
        <input id="minBaselineSales" formControlName="minBaselineSales" type="number"
          min="1" max="1000"
          class="h-8 w-full rounded-[var(--radius-md)] border px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
          [class]="isInvalid('minBaselineSales') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
        @if (isInvalid('minBaselineSales')) {
          <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.velocity.min_baseline_range' | translate }}</p>
        }
      </div>

      <div class="grid grid-cols-2 gap-4">
        <!-- Short window -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="velocityWindowShortDays" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.velocity.short_window' | translate }}
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.velocity.short_window' | translate }}</span>
            </span>
          </div>
          <input id="velocityWindowShortDays" formControlName="velocityWindowShortDays" type="number"
            min="3" max="14"
            class="h-8 w-full rounded-[var(--radius-md)] border px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
            [class]="isInvalid('velocityWindowShortDays') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
          @if (isInvalid('velocityWindowShortDays')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.velocity.short_window_range' | translate }}</p>
          }
        </div>

        <!-- Long window -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="velocityWindowLongDays" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.velocity.long_window' | translate }}
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.velocity.long_window' | translate }}</span>
            </span>
          </div>
          <input id="velocityWindowLongDays" formControlName="velocityWindowLongDays" type="number"
            min="14" max="90"
            class="h-8 w-full rounded-[var(--radius-md)] border px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
            [class]="isInvalid('velocityWindowLongDays') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
          @if (isInvalid('velocityWindowLongDays')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.velocity.long_window_range' | translate }}</p>
          }
        </div>
      </div>

      <!-- Rounding -->
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label for="roundingStep" class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'pricing.form.velocity.rounding_step' | translate }}
          </label>
          <input id="roundingStep" formControlName="roundingStep" type="number" min="1" max="100"
            class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]" />
        </div>
        <div>
          <label for="roundingDirection" class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'pricing.form.velocity.rounding_direction' | translate }}
          </label>
          <select id="roundingDirection" formControlName="roundingDirection"
            class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]">
            @for (rd of roundingDirections; track rd.value) {
              <option [value]="rd.value">{{ rd.labelKey | translate }}</option>
            }
          </select>
        </div>
      </div>

    </div>
  `,
})
export class VelocityAdaptiveFormComponent {
  readonly icons = { Info };
  readonly form = input.required<FormGroup>();
  readonly submitted = input(false);

  readonly roundingDirections: { value: RoundingDirection; labelKey: string }[] = [
    { value: 'FLOOR', labelKey: 'pricing.policies.rounding.FLOOR' },
    { value: 'NEAREST', labelKey: 'pricing.policies.rounding.NEAREST' },
    { value: 'CEIL', labelKey: 'pricing.policies.rounding.CEIL' },
  ];

  hasError(path: string, error: string): boolean {
    const ctrl = this.form().get(path);
    return !!ctrl && ctrl.hasError(error) && (ctrl.touched || this.submitted());
  }

  isInvalid(path: string): boolean {
    const ctrl = this.form().get(path);
    return !!ctrl && ctrl.invalid && (ctrl.touched || this.submitted());
  }
}
