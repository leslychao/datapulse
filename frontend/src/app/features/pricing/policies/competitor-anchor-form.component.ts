import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';

import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, Info } from 'lucide-angular';

import { CompetitorPriceAggregation, RoundingDirection } from '@core/models';

@Component({
  selector: 'dp-competitor-anchor-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, LucideAngularModule],
  template: `
    <div [formGroup]="form()" class="space-y-4">

      <div class="grid grid-cols-2 gap-4">
        <!-- Position factor -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="positionFactor" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.competitor.position_factor' | translate }}<span class="ml-0.5 text-[var(--status-error)]">*</span>
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.competitor.position_factor' | translate }}</span>
            </span>
          </div>
          <input id="positionFactor" formControlName="positionFactor" type="number"
            min="0.5" max="2.0" step="0.05"
            class="h-8 w-full rounded-[var(--radius-md)] border px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
            [class]="isInvalid('positionFactor') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
          @if (isInvalid('positionFactor')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.competitor.position_factor_range' | translate }}</p>
          }
        </div>

        <!-- Min margin -->
        <div>
          <div class="mb-1 flex items-center gap-1.5">
            <label for="minMarginPct" class="text-[var(--text-sm)] text-[var(--text-secondary)]">
              {{ 'pricing.form.competitor.min_margin' | translate }}<span class="ml-0.5 text-[var(--status-error)]">*</span>
            </label>
            <span class="group relative inline-flex">
              <lucide-icon [name]="icons.Info" [size]="13"
                class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
              <span class="dp-tooltip">{{ 'pricing.form.tooltip.competitor.min_margin' | translate }}</span>
            </span>
          </div>
          <div class="relative">
            <input id="minMarginPct" formControlName="minMarginPct" type="number" min="1" max="50"
              class="h-8 w-full rounded-[var(--radius-md)] border px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
              [class]="isInvalid('minMarginPct') ? 'border-[var(--status-error)]' : 'border-[var(--border-default)] bg-[var(--bg-primary)]'" />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-xs)] text-[var(--text-tertiary)]">%</span>
          </div>
          @if (isInvalid('minMarginPct')) {
            <p class="mt-1 text-[var(--text-xs)] text-[var(--status-error)]">{{ 'pricing.form.validation.competitor.min_margin_range' | translate }}</p>
          }
        </div>
      </div>

      <!-- Aggregation (radio buttons) -->
      <div>
        <div class="mb-1.5 flex items-center gap-1.5">
          <span class="text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'pricing.form.competitor.aggregation' | translate }}
          </span>
          <span class="group relative inline-flex">
            <lucide-icon [name]="icons.Info" [size]="13"
              class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
            <span class="dp-tooltip">{{ 'pricing.form.tooltip.competitor.aggregation' | translate }}</span>
          </span>
        </div>
        <div class="inline-flex overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
          @for (agg of aggregations; track agg.value) {
            <label
              class="relative cursor-pointer select-none px-3.5 py-1.5 text-[var(--text-sm)] transition-colors"
              [class]="form().get('aggregation')!.value === agg.value
                ? 'bg-[var(--accent-subtle)] font-medium text-[var(--accent-primary)]'
                : 'bg-[var(--bg-primary)] text-[var(--text-secondary)] hover:bg-[var(--bg-secondary)]'"
            >
              <input type="radio" formControlName="aggregation" [value]="agg.value" class="sr-only" />
              {{ agg.labelKey | translate }}
            </label>
          }
        </div>
      </div>

      <!-- Use margin floor -->
      <div>
        <label class="flex cursor-pointer items-center gap-2">
          <input type="checkbox" formControlName="useMarginFloor"
            class="h-3.5 w-3.5 rounded accent-[var(--accent-primary)]" />
          <span class="text-[var(--text-sm)] text-[var(--text-primary)]">
            {{ 'pricing.form.competitor.use_margin_floor' | translate }}
          </span>
          <span class="group relative inline-flex">
            <lucide-icon [name]="icons.Info" [size]="13"
              class="cursor-help text-[var(--text-tertiary)] transition-colors group-hover:text-[var(--text-secondary)]" />
            <span class="dp-tooltip">{{ 'pricing.form.tooltip.competitor.use_margin_floor' | translate }}</span>
          </span>
        </label>
      </div>

      <!-- Rounding -->
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label for="roundingStep" class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'pricing.form.competitor.rounding_step' | translate }}
          </label>
          <input id="roundingStep" formControlName="roundingStep" type="number" min="1" max="100"
            class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]" />
        </div>
        <div>
          <label for="roundingDirection" class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'pricing.form.competitor.rounding_direction' | translate }}
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
export class CompetitorAnchorFormComponent {
  readonly icons = { Info };
  readonly form = input.required<FormGroup>();
  readonly submitted = input(false);

  readonly aggregations: { value: CompetitorPriceAggregation; labelKey: string }[] = [
    { value: 'MIN', labelKey: 'pricing.form.competitor.aggregation.MIN' },
    { value: 'MEDIAN', labelKey: 'pricing.form.competitor.aggregation.MEDIAN' },
    { value: 'AVG', labelKey: 'pricing.form.competitor.aggregation.AVG' },
  ];

  readonly roundingDirections: { value: RoundingDirection; labelKey: string }[] = [
    { value: 'FLOOR', labelKey: 'pricing.policies.rounding.FLOOR' },
    { value: 'NEAREST', labelKey: 'pricing.policies.rounding.NEAREST' },
    { value: 'CEIL', labelKey: 'pricing.policies.rounding.CEIL' },
  ];

  isInvalid(path: string): boolean {
    const ctrl = this.form().get(path);
    return !!ctrl && ctrl.invalid && (ctrl.touched || this.submitted());
  }
}
