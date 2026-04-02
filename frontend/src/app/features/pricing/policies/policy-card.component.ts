import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { PricingPolicySummary } from '@core/models';
import { formatRelativeTime } from '@shared/utils/format.utils';

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'var(--status-info)',
  ACTIVE: 'var(--status-success)',
  PAUSED: 'var(--status-warning)',
  ARCHIVED: 'var(--text-tertiary)',
};

const MODE_COLOR: Record<string, string> = {
  RECOMMENDATION: 'var(--status-info)',
  SEMI_AUTO: 'var(--status-warning)',
  FULL_AUTO: 'var(--status-success)',
  SIMULATED: 'var(--status-neutral)',
};

@Component({
  selector: 'dp-policy-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <div
      class="group flex h-full cursor-pointer flex-col overflow-hidden
             rounded-[var(--radius-lg)] border border-[var(--border-default)]
             bg-[var(--bg-primary)] transition-shadow duration-200
             hover:shadow-[var(--shadow-md)]"
      [style.border-top]="'3px solid ' + statusColor()"
      (click)="selected.emit()"
    >
      <!-- Body -->
      <div class="flex flex-1 flex-col px-5 pb-4 pt-4">
        <div class="flex items-start justify-between gap-3">
          <button
            class="min-w-0 cursor-pointer truncate text-left
                   text-[length:var(--text-base)] font-semibold
                   text-[var(--text-primary)] transition-colors
                   group-hover:text-[var(--accent-primary)]"
            [title]="policy().name"
            (click)="edit.emit(); $event.stopPropagation()"
          >
            {{ policy().name }}
          </button>
          <span
            class="flex shrink-0 items-center gap-1.5 rounded-full
                   px-2 py-0.5 text-[11px] font-medium"
            [style.color]="statusColor()"
            [style.background-color]="statusBg()"
          >
            <span
              class="inline-block h-1.5 w-1.5 rounded-full"
              [style.background-color]="statusColor()"
            ></span>
            {{ 'pricing.policies.status.' + policy().status | translate }}
          </span>
        </div>

        <div class="mt-3 flex items-center gap-2">
          <span
            class="inline-flex rounded-[var(--radius-md)] bg-[var(--bg-tertiary)]
                   px-2.5 py-1 text-[11px] font-medium text-[var(--text-secondary)]"
          >
            {{ 'pricing.policies.strategy.' + policy().strategyType | translate }}
          </span>
          <span
            class="inline-flex rounded-[var(--radius-md)] px-2.5 py-1
                   text-[11px] font-medium"
            [style.color]="modeColor()"
            [style.background-color]="modeBg()"
          >
            {{ 'pricing.policies.mode.' + policy().executionMode | translate }}
          </span>
          <span class="ml-auto font-mono text-[11px] text-[var(--text-tertiary)]">
            v{{ policy().version }}
          </span>
        </div>
      </div>

      <!-- Stats -->
      <div class="border-t border-[var(--border-subtle)] px-5 py-3">
        <div class="flex items-center gap-x-2 text-[11px] text-[var(--text-tertiary)]">
          <span>
            {{ policy().assignmentsCount }}
            {{ 'pricing.policies.card.assignments_short' | translate }}
          </span>
          <span class="text-[var(--border-default)]">&middot;</span>
          <span>
            {{ 'pricing.policies.card.priority_short' | translate }}
            {{ policy().priority }}
          </span>
        </div>
        <p class="mt-1 text-[11px] text-[var(--text-tertiary)]">
          {{ 'pricing.policies.card.updated' | translate }} {{ updatedAgo() }}
        </p>
      </div>

      <!-- Actions -->
      @if (showActions()) {
        <div
          class="flex items-center gap-0.5 border-t border-[var(--border-subtle)]
                 bg-[var(--bg-secondary)] px-4 py-1.5"
          (click)="$event.stopPropagation()"
        >
          <button
            class="action-btn"
            (click)="edit.emit()"
            [title]="'actions.edit' | translate"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"/>
            </svg>
          </button>

          @if (policy().status === 'DRAFT' || policy().status === 'PAUSED') {
            <button
              class="action-btn text-[var(--status-success)]"
              (click)="activate.emit()"
              [title]="'actions.activate' | translate"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polygon points="6 3 20 12 6 21 6 3"/>
              </svg>
            </button>
          }

          @if (policy().status === 'ACTIVE') {
            <button
              class="action-btn text-[var(--status-warning)]"
              (click)="pause.emit()"
              [title]="'actions.pause' | translate"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="14" y="4" width="4" height="16" rx="1"/>
                <rect x="6" y="4" width="4" height="16" rx="1"/>
              </svg>
            </button>
          }

          @if (policy().status !== 'ARCHIVED') {
            <button
              class="action-btn"
              (click)="archive.emit()"
              [title]="'actions.archive' | translate"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect width="20" height="5" x="2" y="3" rx="1"/>
                <path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8"/>
                <path d="M10 12h4"/>
              </svg>
            </button>
          }
        </div>
      }
    </div>
  `,
})
export class PolicyCardComponent {
  readonly policy = input.required<PricingPolicySummary>();
  readonly showActions = input(true);
  readonly selected = output<void>();
  readonly edit = output<void>();
  readonly activate = output<void>();
  readonly pause = output<void>();
  readonly archive = output<void>();

  protected readonly statusColor = computed(
    () => STATUS_COLOR[this.policy().status] ?? STATUS_COLOR['DRAFT'],
  );

  protected readonly statusBg = computed(
    () => `color-mix(in srgb, ${this.statusColor()} 12%, transparent)`,
  );

  protected readonly modeColor = computed(
    () => MODE_COLOR[this.policy().executionMode] ?? MODE_COLOR['RECOMMENDATION'],
  );

  protected readonly modeBg = computed(
    () => `color-mix(in srgb, ${this.modeColor()} 12%, transparent)`,
  );

  protected readonly updatedAgo = computed(
    () => formatRelativeTime(this.policy().updatedAt),
  );
}
