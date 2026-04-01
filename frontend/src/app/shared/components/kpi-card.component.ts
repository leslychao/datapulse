import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { LucideAngularModule, TrendingUp, TrendingDown, Minus } from 'lucide-angular';

type TrendDirection = 'up' | 'down' | 'neutral';

const TREND_COLORS: Record<TrendDirection, string> = {
  up: 'var(--finance-positive)',
  down: 'var(--finance-negative)',
  neutral: 'var(--finance-zero)',
};

@Component({
  selector: 'dp-kpi-card',
  standalone: true,
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="flex min-w-[160px] flex-col justify-between rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]"
      style="height: 80px; padding: var(--space-3)"
    >
      @if (loading()) {
        <div class="dp-shimmer h-3 w-20 rounded-[var(--radius-sm)]"></div>
        <div class="dp-shimmer h-6 w-24 rounded-[var(--radius-sm)]"></div>
        <div class="dp-shimmer h-3 w-16 rounded-[var(--radius-sm)]"></div>
      } @else {
        <span class="truncate text-[length:var(--text-sm)] text-[var(--text-secondary)]">
          {{ label() }}
        </span>

        <span class="font-mono text-[length:var(--text-2xl)] font-bold text-[var(--text-primary)]">
          {{ displayValue() }}
        </span>

        @if (trend() !== null) {
          <span
            class="inline-flex items-center gap-0.5 text-[length:var(--text-xs)]"
            [style.color]="trendColor()"
          >
            <lucide-icon [img]="trendIcon()" [size]="14"></lucide-icon>
            {{ formattedTrend() }}
          </span>
        } @else {
          <span class="h-[14px]"></span>
        }
      }
    </div>
  `,
})
export class KpiCardComponent {
  readonly label = input.required<string>();
  readonly value = input<string | number | null>(null);
  readonly trend = input<number | null>(null);
  readonly trendDirection = input<TrendDirection>('neutral');
  readonly icon = input<string | null>(null);
  readonly loading = input(false);

  readonly TrendingUp = TrendingUp;
  readonly TrendingDown = TrendingDown;
  readonly Minus = Minus;

  protected readonly displayValue = computed(() => {
    const v = this.value();
    return v === null || v === undefined ? '—' : String(v);
  });

  protected readonly trendColor = computed(() =>
    TREND_COLORS[this.trendDirection()] ?? TREND_COLORS.neutral,
  );

  protected readonly trendIcon = computed(() => {
    switch (this.trendDirection()) {
      case 'up': return this.TrendingUp;
      case 'down': return this.TrendingDown;
      default: return this.Minus;
    }
  });

  protected readonly formattedTrend = computed(() => {
    const t = this.trend();
    if (t === null || t === undefined) return '';
    const abs = Math.abs(t);
    const formatted = abs.toFixed(1).replace('.', ',');
    if (t > 0) return `+${formatted}%`;
    if (t < 0) return `\u2212${formatted}%`;
    return `${formatted}%`;
  });
}
