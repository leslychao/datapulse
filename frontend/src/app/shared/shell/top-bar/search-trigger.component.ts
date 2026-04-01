import { ChangeDetectionStrategy, Component, output } from '@angular/core';
import { LucideAngularModule, Search } from 'lucide-angular';

@Component({
  selector: 'dp-search-trigger',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule],
  template: `
    <button
      (click)="searchRequested.emit()"
      class="flex cursor-pointer items-center gap-2 rounded-[var(--radius-md)] px-2 py-1 text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
    >
      <lucide-icon [img]="Search" [size]="16" />
      <kbd
        class="rounded-[var(--radius-sm)] border border-[var(--border-default)] px-1.5 py-0.5 text-xs text-[var(--text-tertiary)]"
      >
        Ctrl+K
      </kbd>
    </button>
  `,
})
export class SearchTriggerComponent {
  protected readonly Search = Search;
  readonly searchRequested = output();
}
