import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

export interface ProgressStep {
  label: string;
  completed: boolean;
  active: boolean;
}

@Component({
  selector: 'dp-progress-indicator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <div class="flex items-center gap-2">
      @for (step of steps(); track $index; let last = $last) {
        <div class="flex items-center gap-2">
          <div
            class="flex h-7 w-7 items-center justify-center rounded-full text-xs font-semibold transition-colors"
            [class]="step.completed
              ? 'bg-[var(--accent-primary)] text-white'
              : step.active
                ? 'border-2 border-[var(--accent-primary)] text-[var(--accent-primary)]'
                : 'border border-[var(--border-default)] text-[var(--text-tertiary)]'"
          >
            @if (step.completed) {
              <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
              </svg>
            } @else {
              {{ $index + 1 }}
            }
          </div>
          <span
            class="text-sm"
            [class]="step.active ? 'font-medium text-[var(--text-primary)]' : 'text-[var(--text-tertiary)]'"
          >{{ step.label | translate }}</span>
        </div>
        @if (!last) {
          <div
            class="h-px w-8"
            [class]="step.completed ? 'bg-[var(--accent-primary)]' : 'bg-[var(--border-default)]'"
          ></div>
        }
      }
    </div>
  `,
})
export class ProgressIndicatorComponent {
  readonly steps = input<ProgressStep[]>([]);
}
