import {
  ChangeDetectionStrategy,
  Component,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
  selector: 'dp-toggle-switch',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ToggleSwitchComponent),
      multi: true,
    },
  ],
  template: `
    <div
      class="flex items-center gap-2"
      [class.opacity-50]="isDisabled()"
      [class.cursor-not-allowed]="isDisabled()"
    >
      <button
        type="button"
        role="switch"
        [attr.aria-checked]="checked()"
        [disabled]="isDisabled()"
        (click)="toggle()"
        class="relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border transition-colors"
        [class]="checked()
          ? 'bg-[var(--accent-primary)] border-transparent'
          : 'bg-[var(--bg-tertiary)] border-[var(--border-default)]'"
      >
        <span
          class="pointer-events-none inline-block h-4 w-4 rounded-full bg-white shadow-sm transition-transform"
          [class]="checked() ? 'translate-x-4' : 'translate-x-0.5'"
        ></span>
      </button>
      <span class="text-[length:var(--text-base)] text-[var(--text-primary)]">
        {{ label() }}
      </span>
    </div>
  `,
})
export class ToggleSwitchComponent implements ControlValueAccessor {
  readonly label = input.required<string>();
  readonly disabled = input(false);

  protected readonly checked = signal(false);
  protected readonly isDisabled = signal(false);

  private onChange: (val: boolean) => void = () => {};
  private onTouchedFn: () => void = () => {};

  protected toggle(): void {
    if (this.isDisabled()) return;
    const next = !this.checked();
    this.checked.set(next);
    this.onChange(next);
    this.onTouchedFn();
  }

  writeValue(val: boolean): void {
    this.checked.set(!!val);
  }

  registerOnChange(fn: (val: boolean) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouchedFn = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.isDisabled.set(disabled);
  }
}
