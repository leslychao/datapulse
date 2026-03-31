import { Component, inject } from '@angular/core';
import { ToastService, Toast } from './toast.service';

const TYPE_STYLES: Record<Toast['type'], { bg: string; text: string; border: string }> = {
  success: {
    bg: 'color-mix(in srgb, var(--status-success) 8%, transparent)',
    text: 'var(--status-success)',
    border: 'var(--status-success)',
  },
  error: {
    bg: 'color-mix(in srgb, var(--status-error) 8%, transparent)',
    text: 'var(--status-error)',
    border: 'var(--status-error)',
  },
  warning: {
    bg: 'color-mix(in srgb, var(--status-warning) 8%, transparent)',
    text: 'var(--status-warning)',
    border: 'var(--status-warning)',
  },
  info: {
    bg: 'color-mix(in srgb, var(--status-info) 8%, transparent)',
    text: 'var(--status-info)',
    border: 'var(--status-info)',
  },
};

@Component({
  selector: 'dp-toast-container',
  standalone: true,
  template: `
    <div class="fixed bottom-10 right-4 z-[9999] flex flex-col gap-2">
      @for (toast of toastService.toasts(); track toast.id) {
        <div
          class="flex items-center gap-3 rounded-[var(--radius-md)] border px-4 py-2.5 text-sm shadow-[var(--shadow-md)] animate-[slideInRight_200ms_ease]"
          [style.background-color]="style(toast.type).bg"
          [style.color]="style(toast.type).text"
          [style.border-color]="style(toast.type).border"
        >
          <span class="flex-1">{{ toast.message }}</span>
          @if (toast.actionLabel) {
            <button
              (click)="onAction(toast)"
              class="cursor-pointer whitespace-nowrap font-medium underline underline-offset-2"
            >
              {{ toast.actionLabel }}
            </button>
          }
          <button
            (click)="toastService.dismiss(toast.id)"
            class="ml-1 cursor-pointer opacity-60 transition-opacity hover:opacity-100"
          >
            &times;
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    @keyframes slideInRight {
      from { opacity: 0; transform: translateX(16px); }
      to { opacity: 1; transform: translateX(0); }
    }
  `],
})
export class ToastContainerComponent {
  protected readonly toastService = inject(ToastService);

  style(type: Toast['type']) {
    return TYPE_STYLES[type];
  }

  onAction(toast: Toast): void {
    toast.actionFn?.();
    this.toastService.dismiss(toast.id);
  }
}
