import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'dp-confirmation-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    @if (open()) {
      <div class="fixed inset-0 z-[9000] flex items-center justify-center">
        <div class="absolute inset-0 bg-black/40" (click)="onCancel()"></div>
        <div
          class="relative z-10 w-full max-w-md rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-6 shadow-[var(--shadow-md)] animate-[fadeIn_150ms_ease]"
        >
          <h3 class="text-base font-semibold text-[var(--text-primary)]">{{ title() }}</h3>
          <p class="mt-2 text-sm text-[var(--text-secondary)] whitespace-pre-line">{{ message() }}</p>

          @if (typeToConfirm()) {
            <div class="mt-4 flex flex-col gap-1.5">
              <label class="text-sm text-[var(--text-secondary)]">
                Введите <span class="font-medium text-[var(--text-primary)]">{{ typeToConfirm() }}</span> для подтверждения:
              </label>
              <input
                type="text"
                [(ngModel)]="confirmInput"
                class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
              />
            </div>
          }

          <div class="mt-6 flex justify-end gap-3">
            <button
              (click)="onCancel()"
              class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm font-medium text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              {{ cancelLabel() }}
            </button>
            <button
              (click)="onConfirm()"
              [disabled]="!canConfirm()"
              class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm font-medium text-white transition-colors disabled:cursor-not-allowed disabled:opacity-50"
              [class]="danger()
                ? 'bg-[var(--status-error)] hover:bg-[color-mix(in_srgb,var(--status-error)_85%,black)]'
                : 'bg-[var(--accent-primary)] hover:bg-[var(--accent-primary-hover)]'"
            >
              {{ confirmLabel() }}
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
export class ConfirmationModalComponent {
  readonly open = input(false);
  readonly title = input('');
  readonly message = input('');
  readonly confirmLabel = input('Подтвердить');
  readonly cancelLabel = input('Отмена');
  readonly danger = input(false);
  readonly typeToConfirm = input<string | null>(null);

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  protected confirmInput = '';

  protected readonly canConfirm = computed(() => {
    const ttc = this.typeToConfirm();
    if (!ttc) return true;
    return this.confirmInput === ttc;
  });

  onConfirm(): void {
    if (!this.canConfirm()) return;
    this.confirmed.emit();
    this.confirmInput = '';
  }

  onCancel(): void {
    this.cancelled.emit();
    this.confirmInput = '';
  }
}
