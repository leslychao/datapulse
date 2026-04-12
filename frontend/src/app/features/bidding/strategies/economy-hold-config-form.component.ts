import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  input,
  inject,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'dp-economy-hold-config-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe],
  template: `
    <div [formGroup]="parentForm()" class="space-y-4">
      <div class="grid grid-cols-2 gap-4">
        <!-- Target DRR % -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.economy_hold.target_drr_pct' | translate }}
            <span class="ml-0.5 text-[var(--status-error)]">*</span>
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="targetDrrPct"
              min="1"
              max="100"
              placeholder="10"
              class="h-8 w-full rounded-[var(--radius-md)] border px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
              [class]="isInvalid('targetDrrPct')
                ? 'border-[var(--status-error)] bg-[var(--status-error-bg)]/30'
                : 'border-[var(--border-default)] bg-[var(--bg-primary)]'"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-sm)] text-[var(--text-tertiary)]">%</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.economy_hold.target_drr_pct_hint' | translate }}
          </p>
        </div>

        <!-- Tolerance % -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.economy_hold.tolerance_pct' | translate }}
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="tolerancePct"
              min="1"
              max="50"
              placeholder="10"
              class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-sm)] text-[var(--text-tertiary)]">%</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.economy_hold.tolerance_pct_hint' | translate }}
          </p>
        </div>

        <!-- Step Up % -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.economy_hold.step_up_pct' | translate }}
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="stepUpPct"
              min="1"
              max="100"
              placeholder="10"
              class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-sm)] text-[var(--text-tertiary)]">%</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.economy_hold.step_up_pct_hint' | translate }}
          </p>
        </div>

        <!-- Step Down % -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.economy_hold.step_down_pct' | translate }}
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="stepDownPct"
              min="1"
              max="100"
              placeholder="15"
              class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-sm)] text-[var(--text-tertiary)]">%</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.economy_hold.step_down_pct_hint' | translate }}
          </p>
        </div>

        <!-- Max Bid (kopecks) -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.economy_hold.max_bid' | translate }}
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="maxBidKopecks"
              min="0"
              placeholder="50000"
              class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-xs)] text-[var(--text-tertiary)]">{{ 'common.unit.kopecks_short' | translate }}</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.economy_hold.max_bid_hint' | translate }}
          </p>
        </div>

        <!-- Min ROAS -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.economy_hold.min_roas' | translate }}
          </label>
          <input
            type="number"
            formControlName="minRoas"
            min="0"
            step="0.1"
            placeholder="1.0"
            class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
          />
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.economy_hold.min_roas_hint' | translate }}
          </p>
        </div>
      </div>
    </div>
  `,
})
export class EconomyHoldConfigFormComponent implements OnInit {
  readonly parentForm = input.required<FormGroup>();
  readonly submitted = input(false);

  ngOnInit(): void {
    const form = this.parentForm();
    if (!form.contains('targetDrrPct')) {
      form.addControl('targetDrrPct', new FormControl(null, [Validators.required, Validators.min(1), Validators.max(100)]));
    }
    if (!form.contains('tolerancePct')) {
      form.addControl('tolerancePct', new FormControl(10, [Validators.min(1), Validators.max(50)]));
    }
    if (!form.contains('stepUpPct')) {
      form.addControl('stepUpPct', new FormControl(10, [Validators.min(1), Validators.max(100)]));
    }
    if (!form.contains('stepDownPct')) {
      form.addControl('stepDownPct', new FormControl(15, [Validators.min(1), Validators.max(100)]));
    }
    if (!form.contains('maxBidKopecks')) {
      form.addControl('maxBidKopecks', new FormControl(null));
    }
    if (!form.contains('minRoas')) {
      form.addControl('minRoas', new FormControl(1.0, [Validators.min(0)]));
    }
  }

  isInvalid(path: string): boolean {
    const ctrl = this.parentForm().get(path);
    return !!ctrl && ctrl.invalid && (ctrl.touched || this.submitted());
  }
}
