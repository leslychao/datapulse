import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, Plus, Trash2, Info } from 'lucide-angular';

import { RoundingDirection, StrategyType } from '@core/models';

const ALLOWED_COMPONENT_TYPES: { value: StrategyType; labelKey: string }[] = [
  { value: 'TARGET_MARGIN', labelKey: 'pricing.policies.strategy.TARGET_MARGIN' },
  { value: 'PRICE_CORRIDOR', labelKey: 'pricing.policies.strategy.PRICE_CORRIDOR' },
  { value: 'VELOCITY_ADAPTIVE', labelKey: 'pricing.policies.strategy.VELOCITY_ADAPTIVE' },
  { value: 'STOCK_BALANCING', labelKey: 'pricing.policies.strategy.STOCK_BALANCING' },
  { value: 'COMPETITOR_ANCHOR', labelKey: 'pricing.policies.strategy.COMPETITOR_ANCHOR' },
];

@Component({
  selector: 'dp-composite-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, LucideAngularModule],
  template: `
    <div class="space-y-4">

      @for (comp of components().controls; track $index; let i = $index) {
        <div class="rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-4 space-y-3">
          <div class="flex items-center gap-3">
            <!-- Strategy type selector -->
            <div class="flex-1">
              <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
                {{ 'pricing.form.composite.strategy_label' | translate }}
              </label>
              <select
                [formControl]="getControl(i, 'strategyType')"
                (change)="onComponentTypeChange(i)"
                class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]">
                @for (st of allowedTypes; track st.value) {
                  <option [value]="st.value">{{ st.labelKey | translate }}</option>
                }
              </select>
            </div>

            <!-- Weight -->
            <div class="w-28">
              <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
                {{ 'pricing.form.composite.weight_label' | translate }}
              </label>
              <input
                [formControl]="getControl(i, 'weight')"
                type="number" min="0.01" max="1" step="0.05"
                class="h-8 w-full rounded-[var(--radius-md)] border px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
                [class]="getControl(i, 'weight').invalid && (getControl(i, 'weight').touched || submitted())
                  ? 'border-[var(--status-error)]'
                  : 'border-[var(--border-default)] bg-[var(--bg-primary)]'"
              />
            </div>

            <!-- Remove button -->
            <button type="button" (click)="removeComponent(i)"
              class="mt-5 flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-[var(--radius-md)] text-[var(--text-tertiary)] transition-colors hover:bg-[var(--status-error)]/10 hover:text-[var(--status-error)]">
              <lucide-icon [name]="icons.Trash2" [size]="14" />
            </button>
          </div>

          @if (getControl(i, 'weight').invalid && (getControl(i, 'weight').touched || submitted())) {
            <p class="text-[var(--text-xs)] text-[var(--status-error)]">
              {{ 'pricing.form.validation.composite.weight_positive' | translate }}
            </p>
          }

          <!-- Nested strategy params are serialized as JSON string by backend,
               so we don't render nested forms for MVP. The backend receives
               component type + weight only. -->
        </div>
      }

      @if (components().length === 0 && submitted()) {
        <p class="text-[var(--text-xs)] text-[var(--status-error)]">
          {{ 'pricing.form.validation.composite.at_least_one' | translate }}
        </p>
      }

      <button type="button" (click)="addComponent()"
        class="flex h-8 cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-dashed border-[var(--border-default)] bg-[var(--bg-primary)] px-3 text-[var(--text-sm)] text-[var(--text-secondary)] transition-colors hover:border-[var(--accent-primary)] hover:text-[var(--accent-primary)]">
        <lucide-icon [name]="icons.Plus" [size]="14" />
        {{ 'pricing.form.composite.add_strategy' | translate }}
      </button>

      <!-- Rounding -->
      <div [formGroup]="roundingGroup()" class="grid grid-cols-2 gap-4 pt-2">
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'pricing.form.composite.rounding_step' | translate }}
          </label>
          <input formControlName="roundingStep" type="number" min="1" max="100"
            class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]" />
        </div>
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'pricing.form.composite.rounding_direction' | translate }}
          </label>
          <select formControlName="roundingDirection"
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
export class CompositeFormComponent {
  readonly icons = { Plus, Trash2, Info };
  readonly components = input.required<FormArray>();
  readonly roundingGroup = input.required<FormGroup>();
  readonly submitted = input(false);

  readonly allowedTypes = ALLOWED_COMPONENT_TYPES;

  readonly roundingDirections: { value: RoundingDirection; labelKey: string }[] = [
    { value: 'FLOOR', labelKey: 'pricing.policies.rounding.FLOOR' },
    { value: 'NEAREST', labelKey: 'pricing.policies.rounding.NEAREST' },
    { value: 'CEIL', labelKey: 'pricing.policies.rounding.CEIL' },
  ];

  private readonly fb = new FormBuilder();

  getControl(index: number, field: string): FormControl {
    return (this.components().at(index) as FormGroup).get(field)! as FormControl;
  }

  addComponent(): void {
    this.components().push(
      this.fb.group({
        strategyType: ['TARGET_MARGIN' as StrategyType],
        weight: [0.5, [Validators.required, Validators.min(0.01), Validators.max(1)]],
        strategyParams: ['{}'],
      }),
    );
  }

  removeComponent(index: number): void {
    this.components().removeAt(index);
  }

  onComponentTypeChange(_index: number): void {
    // MVP: strategy params are stored as JSON string; no nested form needed
  }
}
