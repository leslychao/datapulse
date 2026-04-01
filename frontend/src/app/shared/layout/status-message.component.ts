import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'dp-status-message',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col items-center gap-4 text-center">
      @switch (icon()) {
        @case ('spinner') {
          <div class="h-6 w-6 animate-spin rounded-full border-2 border-[var(--border-default)] border-t-[var(--accent-primary)]"></div>
        }
        @case ('success') {
          <div class="flex h-10 w-10 items-center justify-center rounded-full bg-[color-mix(in_srgb,var(--status-success)_12%,transparent)]">
            <svg class="h-5 w-5 text-[var(--status-success)]" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          </div>
        }
        @case ('error') {
          <div class="flex h-10 w-10 items-center justify-center rounded-full bg-[color-mix(in_srgb,var(--status-error)_12%,transparent)]">
            <svg class="h-5 w-5 text-[var(--status-error)]" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </div>
        }
        @case ('info') {
          <div class="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--accent-subtle)]">
            <svg class="h-5 w-5 text-[var(--status-info)]" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
        }
        @case ('warning') {
          <div class="flex h-10 w-10 items-center justify-center rounded-full bg-[color-mix(in_srgb,var(--status-warning)_12%,transparent)]">
            <svg class="h-5 w-5 text-[var(--status-warning)]" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
        }
      }

      @if (title()) {
        <h2 class="text-xl font-semibold text-[var(--text-primary)]">{{ title() }}</h2>
      }

      @if (description()) {
        <p class="max-w-md text-base text-[var(--text-secondary)]">{{ description() }}</p>
      }

      @if (actionLabel() || secondaryActionLabel()) {
        <div class="mt-2 flex items-center gap-3">
          @if (actionLabel()) {
            <button
              (click)="actionClick.emit()"
              class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
            >
              {{ actionLabel() }}
            </button>
          }
          @if (secondaryActionLabel()) {
            <button
              (click)="secondaryActionClick.emit()"
              class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm font-medium text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
            >
              {{ secondaryActionLabel() }}
            </button>
          }
        </div>
      }
    </div>
  `,
})
export class StatusMessageComponent {
  readonly icon = input<'spinner' | 'success' | 'error' | 'info' | 'warning'>('spinner');
  readonly title = input('');
  readonly description = input('');
  readonly actionLabel = input('');
  readonly secondaryActionLabel = input('');
  readonly actionClick = output<void>();
  readonly secondaryActionClick = output<void>();
}
