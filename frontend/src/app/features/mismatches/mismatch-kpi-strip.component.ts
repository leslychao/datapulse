import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { MismatchSummary } from '@core/models';

@Component({
  selector: 'dp-mismatch-kpi-strip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <div class="flex flex-wrap items-center gap-2">
      @if (loading()) {
        @for (_ of shimmerSlots; track $index) {
          <div class="dp-shimmer h-7 w-28 rounded-full"></div>
        }
      } @else if (summary()) {
        <span
          class="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[length:var(--text-sm)] font-medium
                 bg-[color-mix(in_srgb,var(--status-warning)_15%,transparent)] text-[var(--status-warning)]"
          [title]="trendTip(summary()!.totalActiveDelta7d)"
        >
          {{ summary()!.totalActive }}
          {{ 'mismatches.kpi.active' | translate }}
        </span>
        <span
          class="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[length:var(--text-sm)] font-medium
                 bg-[color-mix(in_srgb,var(--status-error)_15%,transparent)] text-[var(--status-error)]"
          [title]="trendTip(summary()!.criticalDelta7d)"
        >
          {{ summary()!.criticalCount }}
          {{ 'mismatches.kpi.critical' | translate }}
        </span>
        <span
          class="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[length:var(--text-sm)] font-medium
                 bg-[color-mix(in_srgb,var(--status-info)_15%,transparent)] text-[var(--status-info)]"
          [title]="trendTip(summary()!.avgHoursUnresolvedDelta7d)"
        >
          {{ fmtAvg(summary()!.avgHoursUnresolved) }}
          {{ 'mismatches.kpi.avg_hours' | translate }}
        </span>
        <span
          class="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[length:var(--text-sm)] font-medium
                 bg-[color-mix(in_srgb,var(--status-success)_15%,transparent)] text-[var(--status-success)]"
        >
          {{ summary()!.autoResolvedToday }}
          {{ 'mismatches.kpi.auto_resolved' | translate }}
        </span>
      }
    </div>
  `,
})
export class MismatchKpiStripComponent {
  readonly summary = input<MismatchSummary | null>(null);
  readonly loading = input(false);

  protected readonly shimmerSlots = [1, 2, 3, 4];

  protected fmtAvg(v: number | null | undefined): string {
    if (v == null) return '\u2014';
    return v.toFixed(1).replace('.', ',');
  }

  protected trendTip(delta: number | null | undefined): string {
    if (delta == null) return '';
    const sign = delta > 0 ? '+' : '';
    return `7\u0434: ${sign}${delta}`;
  }
}
