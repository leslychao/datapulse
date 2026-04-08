import {
  ChangeDetectionStrategy,
  Component,
  computed,
  HostListener,
  input,
  output,
  signal,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { DateRangePickerComponent } from '../form/date-range-picker.component';

export interface FilterConfig {
  key: string;
  label: string;
  type: 'text' | 'select' | 'date-range' | 'multi-select';
  options?: { value: string; label: string }[];
}

@Component({
  selector: 'dp-filter-bar',
  standalone: true,
  imports: [TranslatePipe, DateRangePickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-center gap-2">
      @for (filter of filters(); track filter.key) {
        @if (filter.type === 'text') {
          <input
            type="text"
            [value]="getValue(filter.key)"
            [placeholder]="filter.label | translate"
            (input)="onValueChange(filter.key, $event)"
            class="h-8 w-40 rounded-[var(--radius-md)] border border-[var(--border-default)]
                   bg-[var(--bg-primary)] px-3 text-[length:var(--text-sm)]
                   text-[var(--text-primary)] outline-none transition-colors
                   placeholder:text-[var(--text-tertiary)]
                   focus:border-[var(--accent-primary)]"
          />
        }

        @if (filter.type === 'select') {
          <select
            [value]="getValue(filter.key)"
            (change)="onValueChange(filter.key, $event)"
            class="h-8 rounded-[var(--radius-md)] border border-[var(--border-default)]
                   bg-[var(--bg-primary)] px-3 pr-8 text-[length:var(--text-sm)]
                   text-[var(--text-primary)] outline-none transition-colors
                   focus:border-[var(--accent-primary)]"
          >
            <option value="">{{ filter.label | translate }}</option>
            @for (opt of filter.options ?? []; track opt.value) {
              <option [value]="opt.value">{{ opt.label | translate }}</option>
            }
          </select>
        }

        @if (filter.type === 'multi-select') {
          <div class="relative">
            <button
              type="button"
              (click)="toggleDropdown(filter.key)"
              class="relative z-[45] flex h-8 cursor-pointer items-center gap-1.5
                     rounded-[var(--radius-md)] border px-3 text-[length:var(--text-sm)]
                     transition-colors"
              [class]="selectedCount(filter.key) > 0
                ? 'border-[var(--accent-primary)] bg-[var(--accent-subtle)] text-[var(--accent-primary)]'
                : 'border-[var(--border-default)] bg-[var(--bg-primary)] text-[var(--text-secondary)] hover:border-[var(--accent-primary)] hover:text-[var(--text-primary)]'"
            >
              {{ filter.label | translate }}
              @if (selectedCount(filter.key) > 0) {
                <span
                  class="flex h-[18px] min-w-[18px] items-center justify-center rounded-full
                         bg-[var(--accent-primary)] px-1 text-[11px] font-semibold leading-none text-white"
                >
                  {{ selectedCount(filter.key) }}
                </span>
              }
              <svg
                class="h-3.5 w-3.5 transition-transform"
                [class.rotate-180]="openDropdown() === filter.key"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="m6 9 6 6 6-6" />
              </svg>
            </button>

            @if (openDropdown() === filter.key) {
              <div
                class="fixed inset-0 z-40"
                (click)="openDropdown.set(null)"
              ></div>
              <div
                class="absolute left-0 top-[calc(100%+4px)] z-50 min-w-[180px]
                       rounded-[var(--radius-md)] border border-[var(--border-default)]
                       bg-[var(--bg-primary)] py-1 shadow-[var(--shadow-md)]"
              >
                @for (opt of filter.options ?? []; track opt.value) {
                  <label
                    class="flex cursor-pointer items-center gap-2.5 px-3 py-1.5
                           text-[length:var(--text-sm)] text-[var(--text-primary)]
                           transition-colors hover:bg-[var(--bg-secondary)]"
                  >
                    <input
                      type="checkbox"
                      [checked]="isSelected(filter.key, opt.value)"
                      (change)="onCheckboxChange(filter.key, opt.value, $event)"
                      class="h-3.5 w-3.5 accent-[var(--accent-primary)]"
                    />
                    {{ opt.label | translate }}
                  </label>
                }

                @if (selectedCount(filter.key) > 0) {
                  <div class="mt-1 border-t border-[var(--border-subtle)] px-3 pt-1.5">
                    <button
                      type="button"
                      (click)="clearFilter(filter.key)"
                      class="cursor-pointer pb-1 text-[length:var(--text-xs)]
                             text-[var(--text-tertiary)] transition-colors
                             hover:text-[var(--text-primary)]"
                    >
                      {{ 'filter_bar.reset' | translate }}
                    </button>
                  </div>
                }
              </div>
            }
          </div>
        }

        @if (filter.type === 'date-range') {
          <dp-date-range-picker
            [from]="getDateRangeValue(filter.key, 'from')"
            [to]="getDateRangeValue(filter.key, 'to')"
            (fromChange)="onDateRangePartChange(filter.key, 'from', $event)"
            (toChange)="onDateRangePartChange(filter.key, 'to', $event)"
          />
        }
      }

      @if (hasActiveFilters()) {
        <button
          type="button"
          (click)="resetAll()"
          class="h-8 cursor-pointer rounded-[var(--radius-md)] px-3 text-[length:var(--text-sm)]
                 text-[var(--text-tertiary)] transition-colors
                 hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
        >
          {{ 'filter_bar.reset_all' | translate }}
        </button>
      }
    </div>
  `,
})
export class FilterBarComponent {
  readonly filters = input<FilterConfig[]>([]);
  readonly values = input<Record<string, any>>({});
  readonly filtersChanged = output<Record<string, any>>();

  protected readonly openDropdown = signal<string | null>(null);

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.openDropdown.set(null);
  }

  protected readonly hasActiveFilters = computed(() => {
    const vals = this.values();
    return Object.values(vals).some(
      (v) =>
        v !== '' &&
        v !== null &&
        v !== undefined &&
        !(Array.isArray(v) && v.length === 0),
    );
  });

  protected getValue(key: string): string {
    return this.values()[key] ?? '';
  }

  protected getDateRangeValue(key: string, part: 'from' | 'to'): string {
    const range = this.values()[key];
    if (!range || typeof range !== 'object') return '';
    return range[part] ?? '';
  }

  protected isSelected(key: string, value: string): boolean {
    const selected = this.values()[key];
    return Array.isArray(selected) && selected.includes(value);
  }

  protected selectedCount(key: string): number {
    const selected = this.values()[key];
    return Array.isArray(selected) ? selected.length : 0;
  }

  protected toggleDropdown(key: string): void {
    this.openDropdown.set(this.openDropdown() === key ? null : key);
  }

  protected onCheckboxChange(
    key: string,
    value: string,
    event: Event,
  ): void {
    const checked = (event.target as HTMLInputElement).checked;
    const current = this.values()[key];
    const selected = Array.isArray(current) ? [...current] : [];

    if (checked) {
      if (!selected.includes(value)) selected.push(value);
    } else {
      const idx = selected.indexOf(value);
      if (idx >= 0) selected.splice(idx, 1);
    }

    this.emitChange(key, selected.length ? selected : '');
  }

  protected clearFilter(key: string): void {
    this.emitChange(key, '');
    this.openDropdown.set(null);
  }

  protected onValueChange(key: string, event: Event): void {
    const target = event.target as HTMLInputElement | HTMLSelectElement;
    this.emitChange(key, target.value || '');
  }

  protected onDateRangePartChange(
    key: string,
    part: 'from' | 'to',
    value: string,
  ): void {
    const current = this.values()[key] ?? {};
    const range = typeof current === 'object' ? { ...current } : {};
    range[part] = value || '';

    const hasValue = range['from'] || range['to'];
    this.emitChange(key, hasValue ? range : '');
  }

  protected resetAll(): void {
    this.filtersChanged.emit({});
    this.openDropdown.set(null);
  }

  private emitChange(key: string, value: any): void {
    const updated = { ...this.values(), [key]: value };
    const cleaned: Record<string, any> = {};
    for (const [k, v] of Object.entries(updated)) {
      if (v !== '' && v !== null && v !== undefined) {
        cleaned[k] = v;
      }
    }
    this.filtersChanged.emit(cleaned);
  }
}
