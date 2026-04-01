import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'dp-minimal-top-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <header
      class="flex h-10 shrink-0 items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-primary)] px-4"
    >
      <div class="flex items-center gap-2">
        <span class="text-sm font-semibold text-[var(--text-primary)]">Datapulse</span>
      </div>
      <div class="flex items-center gap-3">
        <span class="text-xs text-[var(--text-secondary)]">{{ userEmail() || userName() }}</span>
        <button
          (click)="logoutClick.emit()"
          class="cursor-pointer rounded-[var(--radius-sm)] px-2 py-1 text-xs text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
        >
          {{ 'common.logout' | translate }}
        </button>
      </div>
    </header>
  `,
})
export class MinimalTopBarComponent {
  readonly userName = input('');
  readonly userEmail = input('');
  readonly logoutClick = output<void>();
}
