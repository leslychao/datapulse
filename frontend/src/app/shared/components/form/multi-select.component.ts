import { ChangeDetectionStrategy, Component, computed, ElementRef, HostListener, input, output, signal } from '@angular/core';

export interface MultiSelectOption {
  value: string;
  label: string;
  group?: string;
}

@Component({
  selector: 'dp-multi-select',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative">
      <button
        type="button"
        (click)="toggleOpen($event)"
        class="flex h-8 w-full cursor-pointer items-center justify-between gap-2 rounded-[var(--radius-md)] border px-3 text-sm transition-colors"
        [class]="selected().length > 0
          ? 'border-[var(--accent-primary)] bg-[var(--accent-subtle)] text-[var(--accent-primary)]'
          : 'border-[var(--border-default)] bg-[var(--bg-primary)] text-[var(--text-secondary)]'"
        [attr.aria-expanded]="isOpen()"
        [attr.aria-label]="label()"
      >
        <span class="truncate">{{ displayLabel() }}</span>
        @if (selected().length > 0) {
          <span class="flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-[var(--accent-primary)] px-1 text-[11px] font-semibold text-white">
            {{ selected().length }}
          </span>
        }
      </button>

      @if (isOpen()) {
        <div class="absolute left-0 top-[calc(100%+4px)] z-50 min-w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] py-1 shadow-[var(--shadow-md)]">
          @for (opt of options(); track opt.value) {
            <label class="flex cursor-pointer items-center gap-2.5 px-3 py-1.5 text-sm text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-secondary)]">
              <input
                type="checkbox"
                [checked]="isSelected(opt.value)"
                (change)="onToggle(opt.value)"
                class="h-3.5 w-3.5 accent-[var(--accent-primary)]"
              />
              {{ opt.label }}
            </label>
          }
        </div>
      }
    </div>
  `,
})
export class MultiSelectComponent {
  readonly label = input('');
  readonly options = input<MultiSelectOption[]>([]);
  readonly selected = input<string[]>([]);

  readonly selectionChanged = output<string[]>();

  protected readonly isOpen = signal(false);

  constructor(private readonly el: ElementRef) {}

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.el.nativeElement.contains(event.target)) this.isOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.isOpen.set(false);
  }

  protected readonly displayLabel = computed(() => {
    const count = this.selected().length;
    if (count === 0) return this.label();
    return `${this.label()} (${count})`;
  });

  protected isSelected(value: string): boolean {
    return this.selected().includes(value);
  }

  protected toggleOpen(event: Event): void {
    event.stopPropagation();
    this.isOpen.update((v) => !v);
  }

  protected onToggle(value: string): void {
    const current = [...this.selected()];
    const idx = current.indexOf(value);
    if (idx >= 0) current.splice(idx, 1);
    else current.push(value);
    this.selectionChanged.emit(current);
  }
}
