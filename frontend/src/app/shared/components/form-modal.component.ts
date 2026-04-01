import { ChangeDetectionStrategy, Component, HostListener, input, output } from '@angular/core';

@Component({
  selector: 'dp-form-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen()) {
      <div
        class="fixed inset-0 z-[9000] flex items-center justify-center"
        role="dialog"
        aria-modal="true"
        [attr.aria-labelledby]="'form-modal-title'"
      >
        <div class="absolute inset-0 bg-black/40" (click)="onClose()"></div>
        <div
          class="relative z-10 w-full max-w-lg rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] shadow-[var(--shadow-lg)] animate-[fadeIn_150ms_ease] sm:max-w-xl"
        >
          <div class="flex items-center justify-between px-6 pt-5 pb-0">
            <h3 id="form-modal-title" class="text-base font-semibold text-[var(--text-primary)]">
              {{ title() }}
            </h3>
            <button
              (click)="onClose()"
              class="cursor-pointer rounded-[var(--radius-sm)] p-1 text-[var(--text-tertiary)] transition-colors hover:text-[var(--text-primary)]"
              aria-label="Закрыть"
            >
              <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div class="px-6 py-4">
            <ng-content></ng-content>
          </div>

          <div class="flex justify-end gap-3 border-t border-[var(--border-default)] px-6 pb-5 pt-3">
            <button
              (click)="onClose()"
              class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              {{ cancelLabel() }}
            </button>
            <button
              (click)="onSubmit()"
              [disabled]="submitDisabled() || isPending()"
              class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              @if (isPending()) {
                <span
                  class="dp-spinner mr-2 inline-block h-3.5 w-3.5 rounded-full border-2 border-white/30"
                  style="border-top-color: white"
                ></span>
              }
              {{ submitLabel() }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    @keyframes fadeIn {
      from { opacity: 0; transform: scale(0.97); }
      to { opacity: 1; transform: scale(1); }
    }
  `],
})
export class FormModalComponent {
  readonly title = input.required<string>();
  readonly isOpen = input(false);
  readonly submitLabel = input('');
  readonly cancelLabel = input('');
  readonly isPending = input(false);
  readonly submitDisabled = input(false);

  readonly submit = output<void>();
  readonly close = output<void>();

  @HostListener('keydown.escape')
  onEscape(): void {
    if (this.isOpen()) this.onClose();
  }

  onSubmit(): void {
    if (this.submitDisabled() || this.isPending()) return;
    this.submit.emit();
  }

  onClose(): void {
    this.close.emit();
  }
}
