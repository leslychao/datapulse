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
  selector: 'dp-text-input',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TextInputComponent),
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

      <input
        [id]="inputId"
        [type]="type()"
        [placeholder]="placeholder()"
        [disabled]="isDisabled()"
        [value]="value()"
        [attr.aria-invalid]="errorMessage() ? true : null"
        [attr.aria-describedby]="errorMessage() ? errorId : null"
        (input)="onInput($event)"
        (blur)="onTouched()"
        class="h-8 rounded-[var(--radius-md)] border px-2 py-1 text-[length:var(--text-base)] outline-none transition-colors"
        [class]="inputClasses()"
      />

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
export class TextInputComponent implements ControlValueAccessor {
  readonly label = input.required<string>();
  readonly placeholder = input('');
  readonly type = input<'text' | 'email' | 'password'>('text');
  readonly required = input(false);
  readonly errorMessage = input<string | null>(null);
  readonly disabled = input(false);

  protected readonly inputId = 'dp-input-' + Math.random().toString(36).slice(2, 9);
  protected readonly errorId = this.inputId + '-error';

  protected readonly value = signal('');
  protected readonly isDisabled = signal(false);

  private onChange: (val: string) => void = () => {};
  protected onTouched: () => void = () => {};

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
    const val = (event.target as HTMLInputElement).value;
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
