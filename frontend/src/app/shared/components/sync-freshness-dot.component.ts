import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

type FreshnessLevel = 'fresh' | 'warning' | 'stale' | 'unknown';

const FRESHNESS_COLORS: Record<FreshnessLevel, string> = {
  fresh: 'var(--status-success)',
  warning: 'var(--status-warning)',
  stale: 'var(--status-error)',
  unknown: 'var(--status-neutral)',
};

@Component({
  selector: 'dp-sync-freshness-dot',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="inline-block h-2 w-2 rounded-full"
      [style.background-color]="dotColor()"
      [title]="tooltip()"
    ></span>
  `,
})
export class SyncFreshnessDotComponent {
  private readonly translate = inject(TranslateService);

  readonly lastSyncAt = input<string | null>(null);
  readonly thresholdMinutes = input(60);

  protected readonly freshnessLevel = computed<FreshnessLevel>(() => {
    const iso = this.lastSyncAt();
    if (!iso) return 'unknown';

    const syncDate = new Date(iso);
    if (isNaN(syncDate.getTime())) return 'unknown';

    const minutesAgo = (Date.now() - syncDate.getTime()) / 60_000;
    const threshold = this.thresholdMinutes();

    if (minutesAgo <= threshold * 0.5) return 'fresh';
    if (minutesAgo <= threshold) return 'warning';
    return 'stale';
  });

  protected readonly dotColor = computed(() =>
    FRESHNESS_COLORS[this.freshnessLevel()],
  );

  protected readonly tooltip = computed(() => {
    const level = this.freshnessLevel();
    if (level === 'unknown') return this.translate.instant('sync.no_data_tooltip');

    const iso = this.lastSyncAt();
    const timeStr = iso ? new Date(iso).toLocaleString('ru-RU') : '';

    if (level === 'fresh') return this.translate.instant('sync.fresh_tooltip', { time: timeStr });
    if (level === 'warning') return this.translate.instant('sync.warning_tooltip', { time: timeStr });

    const hoursAgo = iso ? Math.round((Date.now() - new Date(iso).getTime()) / 3_600_000) : 0;
    return this.translate.instant('sync.stale_tooltip', { hours: hoursAgo });
  });
}
