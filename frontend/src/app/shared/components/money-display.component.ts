import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'dp-money-display',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isNull()) {
      <span class="font-mono text-[var(--text-tertiary)]">—</span>
    } @else {
      <span class="font-mono" [style.color]="color()">{{ formatted() }}</span>
    }
  `,
})
export class MoneyDisplayComponent {
  readonly value = input<number | null>(null);
  readonly currency = input('₽');
  readonly sign = input(false);
  readonly decimals = input(2);

  protected readonly isNull = computed(() => this.value() === null || this.value() === undefined);

  protected readonly formatted = computed(() => {
    const v = this.value();
    if (v === null || v === undefined) return '';

    const abs = Math.abs(v);
    const fixed = abs.toFixed(this.decimals());
    const [intPart, decPart] = fixed.split('.');

    const withSeparator = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, '\u00A0');
    const number = decPart ? `${withSeparator},${decPart}` : withSeparator;

    const prefix = v < 0 ? '\u2212' : (this.sign() && v > 0 ? '+' : '');
    return `${prefix}${number}\u00A0${this.currency()}`;
  });

  protected readonly color = computed(() => {
    if (!this.sign()) return 'inherit';
    const v = this.value();
    if (v === null || v === undefined || v === 0) return 'var(--finance-zero)';
    return v > 0 ? 'var(--finance-positive)' : 'var(--finance-negative)';
  });
}
