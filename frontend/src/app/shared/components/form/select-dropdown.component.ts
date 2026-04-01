import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface SelectOption {
  value: string;
  label: string;
}

@Component({
  selector: 'dp-select-dropdown',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SelectDropdownComponent),
      multi: true,
    },
  ],
  template: `
    <div class="flex flex-col gap-1">
      <label
        [attr.for]="inputId"
        class="text-[length:var(--text-sm)] text-[var(--text-secondary)]"
      >
        {{ label() }}
        @if (required()) {
          <span class="text-[var(--status-error)]"> *</span>
        }
      </label>

      <div class="relative">
        <select
          [id]="inputId"
          [disabled]="isDisabled()"
          [value]="value()"
          [attr.aria-invalid]="errorMessage() ? true : null"
          [attr.aria-describedby]="errorMessage() ? errorId : null"
          (change)="onSelect($event)"
          (blur)="onTouched()"
          class="h-8 w-full appearance-none rounded-[var(--radius-md)] border py-1 pl-2 pr-8 text-[length:var(--text-base)] outline-none transition-colors"
          [class]="selectClasses()"
        >
          @if (!value()) {
            <option value="" disabled selected>{{ placeholder() }}</option>
          }
          @for (opt of options(); track opt.value) {
            <option [value]="opt.value">{{ opt.label }}</option>
          }
        </select>

        <svg
          class="pointer-events-none absolute right-2 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--text-tertiary)]"
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="m6 9 6 6 6-6" />
        </svg>
      </div>

      @if (errorMessage()) {
        <span
          [id]="errorId"
          class="text-[length:var(--text-xs)] text-[var(--status-error)]"
        >
          {{ errorMessage() }}
        </span>
      }
    </div>
  `,
})
export class SelectDropdownComponent implements ControlValueAccessor {
  readonly label = input.required<string>();
  readonly options = input<SelectOption[]>([]);
  readonly placeholder = input('');
  readonly required = input(false);
  readonly errorMessage = input<string | null>(null);
  readonly disabled = input(false);

  protected readonly inputId = 'dp-input-' + Math.random().toString(36).slice(2, 9);
  protected readonly errorId = this.inputId + '-error';

  protected readonly value = signal('');
  protected readonly isDisabled = signal(false);

  private onChange: (val: string) => void = () => {};
  protected onTouched: () => void = () => {};

  protected readonly selectClasses = computed(() => {
    if (this.isDisabled()) {
      return 'border-[var(--border-default)] bg-[var(--bg-secondary)] text-[var(--text-tertiary)] cursor-not-allowed';
    }
    if (this.errorMessage()) {
      return 'border-[var(--status-error)] bg-[var(--bg-primary)] text-[var(--text-primary)] focus:border-[var(--status-error)]';
    }
    return 'border-[var(--border-default)] bg-[var(--bg-primary)] text-[var(--text-primary)] focus:border-[var(--accent-primary)]';
  });

  protected onSelect(event: Event): void {
    const val = (event.target as HTMLSelectElement).value;
    this.value.set(val);
    this.onChange(val);
  }

  writeValue(val: string): void {
    this.value.set(val ?? '');
  }

  registerOnChange(fn: (val: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.isDisabled.set(disabled);
  }
}
