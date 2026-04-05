import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

export type StockRisk = 'CRITICAL' | 'WARNING' | 'NORMAL';

const RISK_CONFIG: Record<StockRisk, { color: string; bg: string }> = {
  CRITICAL: { color: 'var(--status-error)', bg: 'color-mix(in srgb, var(--status-error) 12%, transparent)' },
  WARNING: { color: 'var(--status-warning)', bg: 'color-mix(in srgb, var(--status-warning) 12%, transparent)' },
  NORMAL: { color: 'var(--status-success)', bg: 'color-mix(in srgb, var(--status-success) 12%, transparent)' },
};

@Component({
  selector: 'dp-stock-risk-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <span
      class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium"
      [style.background-color]="config().bg"
      [style.color]="config().color"
    >
      <span class="inline-block h-1.5 w-1.5 rounded-full" [style.background-color]="config().color"></span>
      {{ 'stock_risk.' + risk() | translate }}
    </span>
  `,
})
export class StockRiskBadgeComponent {
  readonly risk = input.required<StockRisk>();

  protected readonly config = computed(() => RISK_CONFIG[this.risk()] ?? RISK_CONFIG.NORMAL);
}
