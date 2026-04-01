import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'dp-price-change-indicator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="inline-flex items-baseline gap-1 whitespace-nowrap">
      <span class="font-mono text-[var(--text-secondary)] line-through">{{ formattedOld() }}</span>
      <span class="text-[var(--text-tertiary)]">&rarr;</span>
      <span class="font-mono font-semibold text-[var(--text-primary)]">{{ formattedNew() }}</span>
      <span class="font-mono text-[length:var(--text-xs)]" [style.color]="deltaColor()">
        {{ formattedDelta() }}
      </span>
    </span>
  `,
})
export class PriceChangeIndicatorComponent {
  readonly oldPrice = input.required<number>();
  readonly newPrice = input.required<number>();
  readonly currency = input('₽');

  protected readonly formattedOld = computed(() => this.formatMoney(this.oldPrice()));
  protected readonly formattedNew = computed(() => this.formatMoney(this.newPrice()));

  protected readonly deltaPercent = computed(() => {
    const old = this.oldPrice();
    if (old === 0) return 0;
    return ((this.newPrice() - old) / Math.abs(old)) * 100;
  });

  protected readonly formattedDelta = computed(() => {
    const d = this.deltaPercent();
    const abs = Math.abs(d);
    const fixed = abs.toFixed(1).replace('.', ',');
    if (d > 0) return `+${fixed}%`;
    if (d < 0) return `\u2212${fixed}%`;
    return `${fixed}%`;
  });

  protected readonly deltaColor = computed(() => {
    const d = this.deltaPercent();
    if (d > 0) return 'var(--finance-positive)';
    if (d < 0) return 'var(--finance-negative)';
    return 'var(--finance-zero)';
  });

  private formatMoney(v: number): string {
    const abs = Math.abs(v);
    const fixed = abs.toFixed(2);
    const [intPart, decPart] = fixed.split('.');
    const withSeparator = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, '\u00A0');
    const prefix = v < 0 ? '\u2212' : '';
    return `${prefix}${withSeparator},${decPart}\u00A0${this.currency()}`;
  }
}
