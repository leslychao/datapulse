import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';

import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, Info } from 'lucide-angular';

import { RoundingDirection } from '@core/models';

@Component({
  selector: 'dp-stock-balancing-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, LucideAngularModule],
  template: `
    <div [formGroup]="form()" class="space-y-4">

      <div class="grid grid-cols-2 gap-4">
        <!-- Critical days of cover -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="criticalDaysOfCover" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.stock.critical_days' | translate }}
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.stock.critical_days' | translate }}</span>
            </span>
          </div>
          <input id="criticalDaysOfCover" formControlName="criticalDaysOfCover" type="number" min="1" max="30"
            class="h-8 w-full rounded-[var(--radius-md)] border px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
            [class]="isInvalid('criticalDaysOfCover') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
          @if (isInvalid('criticalDaysOfCover')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.stock.critical_days_range' | translate }}</p>
          }
        </div>

        <!-- Overstock days of cover -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="overstockDaysOfCover" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.stock.overstock_days' | translate }}
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.stock.overstock_days' | translate }}</span>
            </span>
          </div>
          <input id="overstockDaysOfCover" formControlName="overstockDaysOfCover" type="number" min="30" max="365"
            class="h-8 w-full rounded-[var(--radius-md)] border px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
            [class]="isInvalid('overstockDaysOfCover') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
          @if (isInvalid('overstockDaysOfCover')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.stock.overstock_days_range' | translate }}</p>
          }
        </div>
      </div>

      @if (form().hasError('criticalLtOverstock') && (form().touched || submitted())) {
        <p class="text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.stock.critical_lt_overstock' | translate }}</p>
      }

      <div class="grid grid-cols-2 gap-4">
        <!-- Stockout markup -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="stockoutMarkupPct" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.stock.stockout_markup' | translate }}
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.stock.stockout_markup' | translate }}</span>
            </span>
          </div>
          <div class="relative">
            <input id="stockoutMarkupPct" formControlName="stockoutMarkupPct" type="number" min="1" max="30"
              class="h-8 w-full rounded-[var(--radius-md)] border px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
              [class]="isInvalid('stockoutMarkupPct') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-xs)] text-[var(--text-tertiary)]">%</span>
          </div>
          @if (isInvalid('stockoutMarkupPct')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.stock.stockout_markup_range' | translate }}</p>
          }
        </div>

        <!-- Overstock discount factor -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="overstockDiscountFactor" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.stock.overstock_discount_factor' | translate }}
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.stock.overstock_discount_factor' | translate }}</span>
            </span>
          </div>
          <div class="relative">
            <input id="overstockDiscountFactor" formControlName="overstockDiscountFactor" type="number" min="1" max="50"
              class="h-8 w-full rounded-[var(--radius-md)] border px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
              [class]="isInvalid('overstockDiscountFactor') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-xs)] text-[var(--text-tertiary)]">%</span>
          </div>
          @if (isInvalid('overstockDiscountFactor')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.stock.overstock_factor_range' | translate }}</p>
          }
        </div>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <!-- Max discount -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="maxDiscountPct" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.stock.max_discount' | translate }}
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.stock.max_discount' | translate }}</span>
            </span>
          </div>
          <div class="relative">
            <input id="maxDiscountPct" formControlName="maxDiscountPct" type="number" min="1" max="50"
              class="h-8 w-full rounded-[var(--radius-md)] border px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
              [class]="isInvalid('maxDiscountPct') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-xs)] text-[var(--text-tertiary)]">%</span>
          </div>
          @if (isInvalid('maxDiscountPct')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.stock.max_discount_range' | translate }}</p>
          }
        </div>

        <!-- Lead time -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="leadTimeDays" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.stock.lead_time' | translate }}
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.stock.lead_time' | translate }}</span>
            </span>
          </div>
          <input id="leadTimeDays" formControlName="leadTimeDays" type="number" min="1" max="180"
            class="h-8 w-full rounded-[var(--radius-md)] border px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
            [class]="isInvalid('leadTimeDays') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
          @if (isInvalid('leadTimeDays')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.stock.lead_time_range' | translate }}</p>
          }
        </div>
      </div>

      <!-- Rounding -->
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label for="roundingStep" class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'pricing.form.stock.rounding_step' | translate }}
          </label>
          <input id="roundingStep" formControlName="roundingStep" type="number" min="1" max="100"
            class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]" />
        </div>
        <div>
          <label for="roundingDirection" class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'pricing.form.stock.rounding_direction' | translate }}
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
export class StockBalancingFormComponent {
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
