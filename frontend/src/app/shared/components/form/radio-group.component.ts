import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

export interface RadioOption {
  value: string;
  label: string;
  description?: string;
}

@Component({
  selector: 'dp-radio-group',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset class="flex flex-col gap-2">
      @if (label()) {
        <legend class="mb-1 text-sm font-medium text-[var(--text-primary)]">{{ label() }}</legend>
      }
      @for (opt of options(); track opt.value) {
        <label
          class="flex cursor-pointer items-start gap-2.5 rounded-[var(--radius-md)] border px-3 py-2 transition-colors"
          [class]="opt.value === selected()
            ? 'border-[var(--accent-primary)] bg-[var(--accent-subtle)]'
            : 'border-[var(--border-default)] hover:border-[var(--accent-primary)]'"
        >
          <input
            type="radio"
            [name]="name()"
            [value]="opt.value"
            [checked]="opt.value === selected()"
            (change)="valueChanged.emit(opt.value)"
            class="mt-0.5 accent-[var(--accent-primary)]"
          />
          <div class="flex flex-col">
            <span class="text-sm font-medium text-[var(--text-primary)]">{{ opt.label }}</span>
            @if (opt.description) {
              <span class="text-xs text-[var(--text-secondary)]">{{ opt.description }}</span>
            }
          </div>
        </label>
      }
    </fieldset>
  `,
})
export class RadioGroupComponent {
  readonly label = input('');
  readonly name = input('radio-group');
  readonly options = input<RadioOption[]>([]);
  readonly selected = input('');
  readonly valueChanged = output<string>();
}
