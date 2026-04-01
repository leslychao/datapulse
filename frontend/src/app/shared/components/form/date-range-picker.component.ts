import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'dp-date-range-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-row gap-3">
      <div class="flex flex-1 flex-col gap-1">
        <label
          [attr.for]="fromId"
          class="text-[length:var(--text-sm)] text-[var(--text-secondary)]"
        >
          {{ labelFrom() }}
        </label>
        <input
          [id]="fromId"
          type="date"
          [value]="fromValue()"
          (input)="onFromChange($event)"
          class="h-8 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 py-1 text-[length:var(--text-base)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
        />
      </div>

      <div class="flex flex-1 flex-col gap-1">
        <label
          [attr.for]="toId"
          class="text-[length:var(--text-sm)] text-[var(--text-secondary)]"
        >
          {{ labelTo() }}
        </label>
        <input
          [id]="toId"
          type="date"
          [value]="toValue()"
          (input)="onToChange($event)"
          class="h-8 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 py-1 text-[length:var(--text-base)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
        />
      </div>
    </div>

    @if (rangeError()) {
      <span class="mt-1 text-[length:var(--text-xs)] text-[var(--status-error)]">
        {{ rangeErrorText }}
      </span>
    }
  `,
})
export class DateRangePickerComponent {
  private readonly translate = inject(TranslateService);

  readonly labelFrom = input('');
  readonly labelTo = input('');
  readonly from = input<string | null>(null);
  readonly to = input<string | null>(null);

  readonly rangeChange = output<{ from: string | null; to: string | null }>();

  protected readonly fromId = 'dp-date-from-' + Math.random().toString(36).slice(2, 9);
  protected readonly toId = 'dp-date-to-' + Math.random().toString(36).slice(2, 9);

  protected readonly fromValue = signal<string | null>(null);
  protected readonly toValue = signal<string | null>(null);

  protected readonly rangeErrorText = this.translate.instant('form.date_range_error');

  protected readonly rangeError = computed(() => {
    const f = this.fromValue();
    const t = this.toValue();
    if (!f || !t) return false;
    return f > t;
  });

  constructor() {
    this.fromValue.set(this.from() ?? null);
    this.toValue.set(this.to() ?? null);
  }

  protected onFromChange(event: Event): void {
    const val = (event.target as HTMLInputElement).value || null;
    this.fromValue.set(val);
    this.emitRange();
  }

  protected onToChange(event: Event): void {
    const val = (event.target as HTMLInputElement).value || null;
    this.toValue.set(val);
    this.emitRange();
  }

  private emitRange(): void {
    this.rangeChange.emit({
      from: this.fromValue(),
      to: this.toValue(),
    });
  }
}
