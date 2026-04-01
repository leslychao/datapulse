import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  HostListener,
  inject,
  signal,
} from '@angular/core';
import { LucideAngularModule, ChevronDown, User, LogOut } from 'lucide-angular';

import { AuthService } from '@core/auth/auth.service';

@Component({
  selector: 'dp-user-menu',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule],
  template: `
    <div class="relative">
      <button
        (click)="toggle()"
        class="flex cursor-pointer items-center gap-1 rounded-[var(--radius-md)] p-0.5 transition-colors hover:bg-[var(--bg-tertiary)]"
      >
        <span
          class="flex h-7 w-7 items-center justify-center rounded-full bg-[var(--accent-subtle)] text-xs font-semibold text-[var(--accent-primary)]"
        >
          {{ initials() }}
        </span>
        <lucide-icon [img]="ChevronDown" [size]="12" class="text-[var(--text-tertiary)]" />
      </button>

      @if (open()) {
        <div
          class="absolute right-0 top-full z-50 mt-1 w-[220px] overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] shadow-[var(--shadow-md)]"
        >
          <div class="border-b border-[var(--border-default)] px-3 py-3">
            <div class="truncate text-sm font-semibold text-[var(--text-primary)]">{{ displayName() }}</div>
            <div class="truncate text-xs text-[var(--text-tertiary)]">{{ email() }}</div>
          </div>

          <div class="py-1">
            <button
              class="flex h-9 w-full cursor-pointer items-center gap-2 px-3 text-sm text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              <lucide-icon [img]="User" [size]="16" class="text-[var(--text-secondary)]" />
              Профиль
            </button>

            <button
              (click)="onLogout()"
              class="flex h-9 w-full cursor-pointer items-center gap-2 px-3 text-sm text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              <lucide-icon [img]="LogOut" [size]="16" class="text-[var(--text-secondary)]" />
              Выйти
            </button>
          </div>
        </div>
      }
    </div>
  `,
})
export class UserMenuComponent {
  protected readonly ChevronDown = ChevronDown;
  protected readonly User = User;
  protected readonly LogOut = LogOut;

  private readonly authService = inject(AuthService);
  private readonly elementRef = inject(ElementRef);

  protected readonly open = signal(false);

  protected readonly displayName = computed(() => {
    const user = this.authService.user();
    return user?.name ?? '';
  });

  protected readonly email = computed(() => {
    const user = this.authService.user();
    return user?.email ?? '';
  });

  protected readonly initials = computed(() => {
    const user = this.authService.user();
    if (!user?.name) return '?';
    const parts = user.name.split(' ');
    return parts
      .slice(0, 2)
      .map((p) => p.charAt(0))
      .join('')
      .toUpperCase() || '?';
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

  protected onLogout(): void {
    this.close();
    this.authService.logout();
  }
}
