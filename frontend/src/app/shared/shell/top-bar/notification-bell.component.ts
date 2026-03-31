import { Component, computed, ElementRef, HostListener, inject, output, signal } from '@angular/core';
import { LucideAngularModule, Bell } from 'lucide-angular';
import { DatePipe } from '@angular/common';

import { NotificationStore } from '@shared/stores/notification.store';
import { AppNotification, NotificationSeverity } from '@core/models';

const SEVERITY_COLORS: Record<NotificationSeverity, string> = {
  CRITICAL: 'bg-red-500',
  WARNING: 'bg-amber-400',
  INFO: 'bg-blue-400',
};

@Component({
  selector: 'dp-notification-bell',
  standalone: true,
  imports: [LucideAngularModule, DatePipe],
  template: `
    <div class="relative">
      <button
        (click)="toggle()"
        class="relative flex cursor-pointer items-center justify-center rounded-[var(--radius-md)] p-1.5 text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
      >
        <lucide-icon [img]="Bell" [size]="18" />

        @if (store.hasUnread()) {
          <span
            class="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold leading-none text-white"
          >
            {{ badgeText() }}
          </span>
        }
      </button>

      @if (open()) {
        <div
          class="absolute right-0 top-full z-50 mt-1 flex w-[360px] max-h-[480px] flex-col overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] shadow-[var(--shadow-md)]"
        >
          <div class="flex items-center justify-between border-b border-[var(--border-default)] px-3 py-2">
            <span class="text-sm font-semibold text-[var(--text-primary)]">Уведомления</span>
            <button
              (click)="onMarkAllRead()"
              class="cursor-pointer text-xs font-medium text-[var(--accent-primary)] transition-colors hover:underline"
            >
              Прочитать все
            </button>
          </div>

          <div class="flex-1 overflow-y-auto">
            @for (n of store.latestNotifications(); track n.id) {
              <button
                (click)="onNotificationClick(n)"
                class="flex w-full cursor-pointer gap-2.5 px-3 py-2.5 text-left transition-colors hover:bg-[var(--bg-tertiary)]"
                [class.border-l-2]="!n.read"
                [class.border-l-[var(--accent-primary)]]="!n.read"
              >
                <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full" [class]="severityDot(n.severity)"></span>

                <div class="flex flex-1 flex-col gap-0.5 overflow-hidden">
                  <span class="truncate text-sm font-medium text-[var(--text-primary)]">{{ n.title }}</span>
                  <span class="line-clamp-2 text-xs text-[var(--text-secondary)]">{{ n.body }}</span>
                  <span class="mt-0.5 text-xs text-[var(--text-tertiary)]">{{ n.createdAt | date:'short' }}</span>
                </div>
              </button>
            } @empty {
              <div class="px-3 py-6 text-center text-xs text-[var(--text-tertiary)]">
                Нет уведомлений
              </div>
            }
          </div>

          <a
            (click)="close()"
            class="block cursor-pointer border-t border-[var(--border-default)] px-3 py-2 text-center text-xs font-medium text-[var(--accent-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
          >
            Показать все
          </a>
        </div>
      }
    </div>
  `,
})
export class NotificationBellComponent {
  protected readonly Bell = Bell;

  protected readonly store = inject(NotificationStore);
  private readonly elementRef = inject(ElementRef);

  protected readonly open = signal(false);

  readonly notificationClicked = output<AppNotification>();

  protected readonly badgeText = computed(() => {
    const count = this.store.unreadCount();
    return count > 99 ? '99+' : String(count);
  });

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.close();
    }
  }

  protected toggle(): void {
    this.open.update((v) => !v);
  }

  protected close(): void {
    this.open.set(false);
  }

  protected severityDot(severity: NotificationSeverity): string {
    return SEVERITY_COLORS[severity];
  }

  protected onMarkAllRead(): void {
    this.store.markAllRead();
  }

  protected onNotificationClick(notification: AppNotification): void {
    this.store.markAsRead(notification.id);
    this.notificationClicked.emit(notification);
    this.close();
  }
}
