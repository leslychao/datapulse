import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

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
    if (level === 'unknown') return 'Нет данных о синхронизации';
    if (level === 'fresh') return 'Данные актуальны';
    if (level === 'warning') return 'Данные могут устареть';

    const iso = this.lastSyncAt();
    if (!iso) return 'Данные устарели';

    const hoursAgo = Math.round((Date.now() - new Date(iso).getTime()) / 3_600_000);
    return `Данные устарели (${hoursAgo} ч назад)`;
  });
}
