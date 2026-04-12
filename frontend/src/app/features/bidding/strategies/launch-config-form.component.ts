import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  input,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'dp-launch-config-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe],
  template: `
    <div [formGroup]="parentForm()" class="space-y-4">
      <div class="grid grid-cols-2 gap-4">
        <!-- Starting Bid -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.launch.starting_bid' | translate }}
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="startingBid"
              min="0"
              placeholder="500"
              class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-xs)] text-[var(--text-tertiary)]">коп</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.launch.starting_bid_hint' | translate }}
          </p>
        </div>

        <!-- Launch Period Days -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.launch.period_days' | translate }}
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="launchPeriodDays"
              min="1"
              max="30"
              placeholder="7"
              class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-xs)] text-[var(--text-tertiary)]">дн</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.launch.period_days_hint' | translate }}
          </p>
        </div>

        <!-- Min Clicks Target -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.launch.min_clicks' | translate }}
          </label>
          <input
            type="number"
            formControlName="minClicksTarget"
            min="1"
            placeholder="50"
            class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
          />
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.launch.min_clicks_hint' | translate }}
          </p>
        </div>

        <!-- Ceiling DRR -->
        <div>
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.launch.ceiling_drr' | translate }}
          </label>
          <div class="relative">
            <input
              type="number"
              formControlName="ceilingDrrPct"
              min="1"
              max="100"
              placeholder="30"
              class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 pr-8 font-mono text-right text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] focus:ring-1 focus:ring-[var(--accent-primary)]/20"
            />
            <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-sm)] text-[var(--text-tertiary)]">%</span>
          </div>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.launch.ceiling_drr_hint' | translate }}
          </p>
        </div>

        <!-- Target Strategy (after launch) -->
        <div class="col-span-2">
          <label class="mb-1 block text-[var(--text-sm)] text-[var(--text-secondary)]">
            {{ 'bidding.strategy.launch.target_strategy' | translate }}
          </label>
          <select
            formControlName="targetStrategy"
            class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 text-[var(--text-sm)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
          >
            <option value="ECONOMY_HOLD">{{ 'bidding.policies.strategy.ECONOMY_HOLD' | translate }}</option>
            <option value="GROWTH">{{ 'bidding.policies.strategy.GROWTH' | translate }}</option>
            <option value="POSITION_HOLD">{{ 'bidding.policies.strategy.POSITION_HOLD' | translate }}</option>
          </select>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.strategy.launch.target_strategy_hint' | translate }}
          </p>
        </div>
      </div>
    </div>
  `,
})
export class LaunchConfigFormComponent implements OnInit {
  readonly parentForm = input.required<FormGroup>();
  readonly submitted = input(false);

  ngOnInit(): void {
    const form = this.parentForm();
    if (!form.contains('startingBid')) {
      form.addControl('startingBid', new FormControl(null));
    }
    if (!form.contains('launchPeriodDays')) {
      form.addControl('launchPeriodDays', new FormControl(7, [Validators.min(1), Validators.max(30)]));
    }
    if (!form.contains('minClicksTarget')) {
      form.addControl('minClicksTarget', new FormControl(50, [Validators.min(1)]));
    }
    if (!form.contains('ceilingDrrPct')) {
      form.addControl('ceilingDrrPct', new FormControl(30, [Validators.min(1), Validators.max(100)]));
    }
    if (!form.contains('targetStrategy')) {
      form.addControl('targetStrategy', new FormControl('ECONOMY_HOLD'));
    }
  }

  isInvalid(path: string): boolean {
    const ctrl = this.parentForm().get(path);
    return !!ctrl && ctrl.invalid && (ctrl.touched || this.submitted());
  }
}
