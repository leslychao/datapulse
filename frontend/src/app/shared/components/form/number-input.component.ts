import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
  selector: 'dp-number-input',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => NumberInputComponent),
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
      </label>

      <div class="relative">
        <input
          [id]="inputId"
          type="number"
          [placeholder]="''"
          [disabled]="isDisabled()"
          [value]="value()"
          [attr.min]="min()"
          [attr.max]="max()"
          [attr.step]="step()"
          [attr.aria-invalid]="errorMessage() ? true : null"
          [attr.aria-describedby]="errorMessage() ? errorId : null"
          (input)="onInput($event)"
          (blur)="onBlur()"
          class="h-8 w-full rounded-[var(--radius-md)] border py-1 pl-2 font-mono text-right text-[length:var(--text-base)] outline-none transition-colors"
          [class]="inputClasses()"
          [style.padding-right]="suffix() ? '2rem' : '0.5rem'"
        />

        @if (suffix()) {
          <span
            class="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-[length:var(--text-sm)] text-[var(--text-secondary)]"
          >
            {{ suffix() }}
          </span>
        }
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
export class NumberInputComponent implements ControlValueAccessor {
  readonly label = input.required<string>();
  readonly min = input<number | null>(null);
  readonly max = input<number | null>(null);
  readonly step = input(1);
  readonly suffix = input<string | null>(null);
  readonly errorMessage = input<string | null>(null);
  readonly disabled = input(false);

  protected readonly inputId = 'dp-input-' + Math.random().toString(36).slice(2, 9);
  protected readonly errorId = this.inputId + '-error';

  protected readonly value = signal<number | null>(null);
  protected readonly isDisabled = signal(false);

  private onChange: (val: number | null) => void = () => {};
  private onTouchedFn: () => void = () => {};

  protected readonly inputClasses = computed(() => {
    if (this.isDisabled()) {
      return 'border-[var(--border-default)] bg-[var(--bg-secondary)] text-[var(--text-tertiary)] cursor-not-allowed';
    }
    if (this.errorMessage()) {
      return 'border-[var(--status-error)] bg-[var(--bg-primary)] text-[var(--text-primary)] focus:border-[var(--status-error)]';
    }
    return 'border-[var(--border-default)] bg-[var(--bg-primary)] text-[var(--text-primary)] focus:border-[var(--accent-primary)]';
  });

  protected onInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    const val = raw === '' ? null : Number(raw);
    this.value.set(val);
    this.onChange(val);
  }

  protected onBlur(): void {
    this.onTouchedFn();
  }

  writeValue(val: number | null): void {
    this.value.set(val ?? null);
  }

  registerOnChange(fn: (val: number | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouchedFn = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.isDisabled.set(disabled);
  }
}
