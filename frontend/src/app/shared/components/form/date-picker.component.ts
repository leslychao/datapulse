import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
  selector: 'dp-date-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DatePickerComponent), multi: true },
  ],
  template: `
    <div class="flex flex-col gap-1.5">
      @if (label()) {
        <label [for]="inputId" class="text-sm font-medium text-[var(--text-primary)]">{{ label() }}</label>
      }
      <input
        type="date"
        [id]="inputId"
        [value]="internalValue()"
        [disabled]="isDisabled()"
        (input)="onInput($event)"
        class="h-8 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 text-sm text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)] disabled:opacity-50"
      />
    </div>
  `,
})
export class DatePickerComponent implements ControlValueAccessor {
  readonly label = input('');

  protected readonly inputId = `dp-date-${Math.random().toString(36).slice(2, 8)}`;
  protected readonly internalValue = signal('');
  protected readonly isDisabled = signal(false);

  private onChange: (val: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(val: string | null): void {
    this.internalValue.set(val ?? '');
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

  protected onInput(event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    this.internalValue.set(val);
    this.onChange(val);
    this.onTouched();
  }
}
