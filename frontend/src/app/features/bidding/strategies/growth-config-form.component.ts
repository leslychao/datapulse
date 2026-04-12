import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  input,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'dp-growth-config-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe],
  template: `
    <div [formGroup]="parentForm()" class="space-y-4">
      <div class="grid grid-cols-2 gap-4">
        <!-- Target CPO -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.growth.target_cpo' | translate }}
            <span class="ml-0.5 text-[var(--status-error)]">*</span>
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="targetCpo"
              min="1"
              step="0.1"
              placeholder="15"
              class="h-8 w-full rounded-[var(--radius-md)] border px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
              [class]="isInvalid('targetCpo')
                ? 'border-[var(--status-error)] bg-[var(--status-error-bg)]/30'
                : 'border-[var(--border-default)] bg-[var(--bg-primary)]'"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-sm)] text-[var(--text-tertiary)]">%</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.growth.target_cpo_hint' | translate }}
          </p>
        </div>

        <!-- Max CPO -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.growth.max_cpo' | translate }}
            <span class="ml-0.5 text-[var(--status-error)]">*</span>
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="maxCpo"
              min="1"
              step="0.1"
              placeholder="25"
              class="h-8 w-full rounded-[var(--radius-md)] border px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
              [class]="isInvalid('maxCpo')
                ? 'border-[var(--status-error)] bg-[var(--status-error-bg)]/30'
                : 'border-[var(--border-default)] bg-[var(--bg-primary)]'"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-sm)] text-[var(--text-tertiary)]">%</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.growth.max_cpo_hint' | translate }}
          </p>
        </div>

        <!-- Bid Step % -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.growth.bid_step_pct' | translate }}
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
            {{ 'bidding.strategy.growth.bid_step_pct_hint' | translate }}
          </p>
        </div>

        <!-- Min Clicks for Signal -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.growth.min_clicks' | translate }}
          </label>
          <input
            type="number"
            formControlName="minClicksForSignal"
            min="1"
            placeholder="10"
            class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
          />
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.growth.min_clicks_hint' | translate }}
          </p>
        </div>

        <!-- Max Bid -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.growth.max_bid' | translate }}
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="maxBid"
              min="0"
              placeholder="50000"
              class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-xs)] text-[var(--text-tertiary)]">коп</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.growth.max_bid_hint' | translate }}
          </p>
        </div>
      </div>
    </div>
  `,
})
export class GrowthConfigFormComponent implements OnInit {
  readonly parentForm = input.required<FormGroup>();
  readonly submitted = input(false);

  ngOnInit(): void {
    const form = this.parentForm();
    if (!form.contains('targetCpo')) {
      form.addControl('targetCpo', new FormControl(null, [Validators.required, Validators.min(1)]));
    }
    if (!form.contains('maxCpo')) {
      form.addControl('maxCpo', new FormControl(null, [Validators.required, Validators.min(1)]));
    }
    if (!form.contains('bidStepPct')) {
      form.addControl('bidStepPct', new FormControl(10, [Validators.min(1), Validators.max(50)]));
    }
    if (!form.contains('minClicksForSignal')) {
      form.addControl('minClicksForSignal', new FormControl(10, [Validators.min(1)]));
    }
    if (!form.contains('maxBid')) {
      form.addControl('maxBid', new FormControl(null));
    }
  }

  isInvalid(path: string): boolean {
    const ctrl = this.parentForm().get(path);
    return !!ctrl && ctrl.invalid && (ctrl.touched || this.submitted());
  }
}
