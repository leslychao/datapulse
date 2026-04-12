import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  input,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'dp-liquidation-config-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe],
  template: `
    <div [formGroup]="parentForm()" class="space-y-4">
      <div class="grid grid-cols-2 gap-4">
        <!-- Max DRR % -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.liquidation.max_drr_pct' | translate }}
            <span class="ml-0.5 text-[var(--status-error)]">*</span>
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="maxDrrPct"
              min="1"
              max="100"
              placeholder="25"
              class="h-8 w-full rounded-[var(--radius-md)] border px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
              [class]="isInvalid('maxDrrPct')
                ? 'border-[var(--status-error)] bg-[var(--status-error-bg)]/30'
                : 'border-[var(--border-default)] bg-[var(--bg-primary)]'"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-sm)] text-[var(--text-tertiary)]">%</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.liquidation.max_drr_pct_hint' | translate }}
          </p>
        </div>

        <!-- Exit Days of Cover -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.liquidation.exit_days' | translate }}
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="exitDaysOfCover"
              min="0"
              max="90"
              placeholder="7"
              class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-xs)] text-[var(--text-tertiary)]">дн</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.liquidation.exit_days_hint' | translate }}
          </p>
        </div>

        <!-- Bid Step % -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.liquidation.bid_step_pct' | translate }}
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="bidStepPct"
              min="1"
              max="50"
              placeholder="15"
              class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-sm)] text-[var(--text-tertiary)]">%</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.liquidation.bid_step_pct_hint' | translate }}
          </p>
        </div>
      </div>
    </div>
  `,
})
export class LiquidationConfigFormComponent implements OnInit {
  readonly parentForm = input.required<FormGroup>();
  readonly submitted = input(false);

  ngOnInit(): void {
    const form = this.parentForm();
    if (!form.contains('maxDrrPct')) {
      form.addControl('maxDrrPct', new FormControl(null, [Validators.required, Validators.min(1), Validators.max(100)]));
    }
    if (!form.contains('exitDaysOfCover')) {
      form.addControl('exitDaysOfCover', new FormControl(7, [Validators.min(0), Validators.max(90)]));
    }
    if (!form.contains('bidStepPct')) {
      form.addControl('bidStepPct', new FormControl(15, [Validators.min(1), Validators.max(50)]));
    }
  }

  isInvalid(path: string): boolean {
    const ctrl = this.parentForm().get(path);
    return !!ctrl && ctrl.invalid && (ctrl.touched || this.submitted());
  }
}
