import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  input,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'dp-position-hold-config-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe],
  template: `
    <div [formGroup]="parentForm()" class="space-y-4">
      <div class="grid grid-cols-2 gap-4">
        <!-- Target Impressions Daily -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.position_hold.target_impressions' | translate }}
            <span class="ml-0.5 text-[var(--status-error)]">*</span>
          </label>
          <input
            type="number"
            formControlName="targetImpressionsDaily"
            min="1"
            placeholder="1000"
            class="h-8 w-full rounded-[var(--radius-md)] border px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            [class]="isInvalid('targetImpressionsDaily')
              ? 'border-[var(--status-error)] bg-[var(--status-error-bg)]/30'
              : 'border-[var(--border-default)] bg-[var(--bg-primary)]'"
          />
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.position_hold.target_impressions_hint' | translate }}
          </p>
        </div>

        <!-- Impressions Tolerance % -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.position_hold.tolerance_pct' | translate }}
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="impressionsTolerancePct"
              min="1"
              max="100"
              placeholder="20"
              class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-sm)] text-[var(--text-tertiary)]">%</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.position_hold.tolerance_pct_hint' | translate }}
          </p>
        </div>

        <!-- Ceiling DRR % -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.position_hold.ceiling_drr_pct' | translate }}
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="ceilingDrrPct"
              min="1"
              max="100"
              placeholder="15"
              class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-sm)] text-[var(--text-tertiary)]">%</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.position_hold.ceiling_drr_pct_hint' | translate }}
          </p>
        </div>

        <!-- Bid Step % -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.position_hold.bid_step_pct' | translate }}
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="bidStepPct"
              min="1"
              max="50"
              placeholder="10"
              class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-sm)] text-[var(--text-tertiary)]">%</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.position_hold.bid_step_pct_hint' | translate }}
          </p>
        </div>
      </div>
    </div>
  `,
})
export class PositionHoldConfigFormComponent implements OnInit {
  readonly parentForm = input.required<FormGroup>();
  readonly submitted = input(false);

  ngOnInit(): void {
    const form = this.parentForm();
    if (!form.contains('targetImpressionsDaily')) {
      form.addControl('targetImpressionsDaily', new FormControl(null, [Validators.required, Validators.min(1)]));
    }
    if (!form.contains('impressionsTolerancePct')) {
      form.addControl('impressionsTolerancePct', new FormControl(20, [Validators.min(1), Validators.max(100)]));
    }
    if (!form.contains('ceilingDrrPct')) {
      form.addControl('ceilingDrrPct', new FormControl(null, [Validators.min(1), Validators.max(100)]));
    }
    if (!form.contains('bidStepPct')) {
      form.addControl('bidStepPct', new FormControl(10, [Validators.min(1), Validators.max(50)]));
    }
  }

  isInvalid(path: string): boolean {
    const ctrl = this.parentForm().get(path);
    return !!ctrl && ctrl.invalid && (ctrl.touched || this.submitted());
  }
}
