import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
  actionLabel?: string;
  actionFn?: () => void;
}

const AUTO_DISMISS_MS = 3000;

@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 0;

  readonly toasts = signal<Toast[]>([]);

  success(message: string): void {
    this.add('success', message);
  }

  error(message: string, actionLabel?: string, actionFn?: () => void): void {
    this.add('error', message, actionLabel, actionFn);
  }

  warning(message: string): void {
    this.add('warning', message);
  }

  info(message: string): void {
    this.add('info', message);
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }

  private add(
    type: Toast['type'],
    message: string,
    actionLabel?: string,
    actionFn?: () => void,
  ): void {
    const id = this.nextId++;
    const toast: Toast = { id, type, message, actionLabel, actionFn };
    this.toasts.update((list) => [...list, toast]);
    setTimeout(() => this.dismiss(id), AUTO_DISMISS_MS);
  }
}
