import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'dp-percent-display',
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
export class PercentDisplayComponent {
  readonly value = input<number | null>(null);
  readonly sign = input(false);
  readonly decimals = input(1);

  protected readonly isNull = computed(() => this.value() === null || this.value() === undefined);

  protected readonly formatted = computed(() => {
    const v = this.value();
    if (v === null || v === undefined) return '';

    const abs = Math.abs(v);
    const fixed = abs.toFixed(this.decimals()).replace('.', ',');

    if (v < 0) return `\u2212${fixed}%`;
    if (v > 0 && this.sign()) return `+${fixed}%`;
    return `${fixed}%`;
  });

  protected readonly color = computed(() => {
    if (!this.sign()) return 'inherit';
    const v = this.value();
    if (v === null || v === undefined || v === 0) return 'var(--finance-zero)';
    return v > 0 ? 'var(--finance-positive)' : 'var(--finance-negative)';
  });
}
