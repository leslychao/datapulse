import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

@Component({
  selector: 'dp-empty-state',
  standalone: true,
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="rounded-[var(--radius-md)] border border-dashed border-[var(--border-default)] bg-[var(--bg-secondary)] py-12 text-center">
      <p class="text-sm text-[var(--text-secondary)]">{{ message() }}</p>
      @if (hint()) {
        <p class="mt-1 text-[var(--text-xs)] text-[var(--text-tertiary)]">{{ hint() }}</p>
      }
      @if (actionLabel()) {
        <button
          (click)="action.emit()"
          class="mt-3 cursor-pointer text-sm text-[var(--accent-primary)] transition-colors hover:underline"
        >
          {{ actionLabel() }}
        </button>
      }
    </div>
  `,
})
export class EmptyStateComponent {
  readonly message = input.required<string>();
  readonly hint = input<string>('');
  readonly actionLabel = input<string>('');
  readonly action = output<void>();
}
