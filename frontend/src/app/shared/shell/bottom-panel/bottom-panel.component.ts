import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { LucideAngularModule, ChevronUp, ChevronDown } from 'lucide-angular';

@Component({
  selector: 'dp-bottom-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule],
  template: `
    @if (isOpen()) {
      <div class="flex flex-col border-t border-[var(--border-default)] bg-[var(--bg-primary)]"
           [style.height.px]="panelHeight">
        <div class="flex h-7 shrink-0 items-center justify-between border-b
                    border-[var(--border-subtle)] px-3">
          <span class="text-[11px] font-medium uppercase tracking-wider text-[var(--text-tertiary)]">
            Массовые действия
          </span>
          <button class="flex h-5 w-5 items-center justify-center rounded-[var(--radius-sm)]
                         text-[var(--text-tertiary)] transition-colors
                         duration-[var(--transition-fast)]
                         hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
                  (click)="toggle()"
                  aria-label="Свернуть панель">
            <lucide-icon [img]="chevronDownIcon" [size]="14" />
          </button>
        </div>
        <div class="flex-1 overflow-auto p-3">
          <ng-content />
        </div>
      </div>
    }
  `,
})
export class BottomPanelComponent {
  readonly chevronUpIcon = ChevronUp;
  readonly chevronDownIcon = ChevronDown;

  readonly isOpen = signal(false);
  readonly panelHeight = 200;

  toggle(): void {
    this.isOpen.update((v) => !v);
  }

  open(): void {
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
  }
}
