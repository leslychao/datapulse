import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { LucideAngularModule, LucideIconData, TrendingUp, TrendingDown, Minus } from 'lucide-angular';

type TrendDirection = 'up' | 'down' | 'neutral';
export type KpiAccent = 'primary' | 'success' | 'warning' | 'error' | 'info' | 'neutral';

const TREND_COLORS: Record<TrendDirection, string> = {
  up: 'var(--finance-positive)',
  down: 'var(--finance-negative)',
  neutral: 'var(--finance-zero)',
};

const ACCENT_STYLES: Record<KpiAccent, { bg: string; fg: string }> = {
  primary: { bg: 'var(--accent-subtle)', fg: 'var(--accent-primary)' },
  success: { bg: 'var(--status-success-bg)', fg: 'var(--status-success)' },
  warning: { bg: 'var(--status-warning-bg)', fg: 'var(--status-warning)' },
  error: { bg: 'var(--status-error-bg)', fg: 'var(--status-error)' },
  info: { bg: 'var(--status-info-bg)', fg: 'var(--status-info)' },
  neutral: { bg: 'var(--status-neutral-bg)', fg: 'var(--status-neutral)' },
};

@Component({
  selector: 'dp-kpi-card',
  standalone: true,
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex min-w-0 flex-1 basis-[150px]' },
  template: `
    <div
      class="flex w-full items-center gap-3 rounded-[var(--radius-lg)] border bg-[var(--bg-primary)] px-3.5 py-2.5 transition-colors"
      [class]="clickable()
        ? (active()
            ? 'cursor-pointer border-[var(--accent-primary)] bg-[var(--accent-subtle)]'
            : 'cursor-pointer border-[var(--border-default)] hover:border-[var(--accent-primary)] hover:bg-[var(--bg-tertiary)]')
        : 'border-[var(--border-default)]'"
      [title]="tooltip()"
      (click)="clickable() && clicked.emit()"
      [attr.role]="clickable() ? 'button' : null"
      [attr.tabindex]="clickable() ? 0 : null"
      (keydown.enter)="clickable() && clicked.emit()"
      (keydown.space)="clickable() && clicked.emit(); $event.preventDefault()"
    >
      @if (icon(); as img) {
        <div
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-[var(--radius-md)]"
          [style.background-color]="accentBg()"
        >
          <lucide-icon [img]="img" [size]="18" [style.color]="accentFg()" />
        </div>
      }

      <div class="flex min-w-0 flex-col">
        @if (loading()) {
          <div class="dp-shimmer mb-1 h-3 w-16 rounded-[var(--radius-sm)]"></div>
          <div class="dp-shimmer h-5 w-20 rounded-[var(--radius-sm)]"></div>
        } @else {
          <div class="flex items-center gap-1.5">
            <span class="truncate text-[length:var(--text-xs)] leading-snug text-[var(--text-secondary)]">
              {{ label() }}
            </span>
            @if (trend() !== null) {
              <span
                class="inline-flex shrink-0 items-center gap-0.5 text-[length:var(--text-xs)] leading-snug"
                [style.color]="trendColor()"
              >
                <lucide-icon [img]="trendIcon()" [size]="11" />
                {{ formattedTrend() }}
              </span>
            }
          </div>
          <span class="font-mono text-[length:var(--text-lg)] font-bold leading-tight text-[var(--text-primary)]">
            {{ displayValue() }}
          </span>
          @if (subtitle()) {
            <span class="truncate text-[length:var(--text-xs)] leading-snug text-[var(--text-tertiary)]">{{ subtitle() }}</span>
          }
        }
      </div>
    </div>
  `,
})
export class KpiCardComponent {
  readonly label = input.required<string>();
  readonly value = input<string | number | null>(null);
  readonly subtitle = input<string>('');
  readonly tooltip = input<string>('');
  readonly trend = input<number | null>(null);
  readonly trendDirection = input<TrendDirection>('neutral');
  readonly icon = input<LucideIconData | null>(null);
  readonly accent = input<KpiAccent>('neutral');
  readonly loading = input(false);
  readonly clickable = input(false);
  readonly active = input(false);
  readonly clicked = output<void>();

  protected readonly accentBg = computed(() =>
    ACCENT_STYLES[this.accent()]?.bg ?? ACCENT_STYLES.neutral.bg,
  );

  protected readonly accentFg = computed(() =>
    ACCENT_STYLES[this.accent()]?.fg ?? ACCENT_STYLES.neutral.fg,
  );

  protected readonly displayValue = computed(() => {
    const v = this.value();
    return v === null || v === undefined ? '—' : String(v);
  });

  protected readonly trendColor = computed(() =>
    TREND_COLORS[this.trendDirection()] ?? TREND_COLORS.neutral,
  );

  protected readonly trendIcon = computed(() => {
    switch (this.trendDirection()) {
      case 'up': return TrendingUp;
      case 'down': return TrendingDown;
      default: return Minus;
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
