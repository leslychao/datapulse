import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { formatDistanceToNow } from 'date-fns';
import { ru } from 'date-fns/locale';

export type DateDisplayMode = 'short' | 'medium' | 'long' | 'relative' | 'absolute' | 'timestamp';

const FORMAT_OPTIONS: Record<string, Intl.DateTimeFormatOptions> = {
  short: { day: '2-digit', month: '2-digit', year: 'numeric' },
  medium: { day: 'numeric', month: 'short', year: 'numeric' },
  long: { day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' },
  absolute: { day: 'numeric', month: 'short', year: 'numeric' },
  timestamp: { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' },
};

@Component({
  selector: 'dp-date-display',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isNull()) {
      <span class="text-[var(--text-tertiary)]">—</span>
    } @else {
      <span class="text-[length:var(--text-sm)] text-[var(--text-primary)]" [title]="tooltip()">
        {{ formatted() }}
      </span>
    }
  `,
})
export class DateDisplayComponent {
  readonly value = input<string | null>(null);
  readonly date = input<string | null>(null);
  readonly format = input<DateDisplayMode>('medium');
  readonly mode = input<DateDisplayMode | null>(null);

  protected readonly resolvedDate = computed(() => this.date() ?? this.value());
  protected readonly resolvedMode = computed(() => this.mode() ?? this.format());
  protected readonly isNull = computed(() => !this.resolvedDate());

  protected readonly formatted = computed(() => {
    const iso = this.resolvedDate();
    if (!iso) return '';

    const d = new Date(iso);
    if (isNaN(d.getTime())) return '—';

    if (this.resolvedMode() === 'relative') {
      return formatDistanceToNow(d, { locale: ru, addSuffix: true });
    }

    const options = FORMAT_OPTIONS[this.resolvedMode()] ?? FORMAT_OPTIONS['medium'];
    return new Intl.DateTimeFormat('ru-RU', options).format(d);
  });

  protected readonly tooltip = computed(() => {
    const iso = this.resolvedDate();
    if (!iso) return '';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    return new Intl.DateTimeFormat('ru-RU', FORMAT_OPTIONS['long']).format(d);
  });
}
