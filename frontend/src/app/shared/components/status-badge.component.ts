import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type StatusColor = 'success' | 'error' | 'warning' | 'info' | 'neutral';

const CSS_VARS: Record<StatusColor, string> = {
  success: 'var(--status-success)',
  error: 'var(--status-error)',
  warning: 'var(--status-warning)',
  info: 'var(--status-info)',
  neutral: 'var(--status-neutral)',
};

@Component({
  selector: 'dp-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium leading-4"
      [style.background-color]="bgColor()"
      [style.color]="textColor()"
    >
      @if (dot()) {
        <span class="inline-block h-1.5 w-1.5 rounded-full" [style.background-color]="textColor()"></span>
      }
      {{ label() }}
    </span>
  `,
})
export class StatusBadgeComponent {
  readonly label = input.required<string>();
  readonly color = input<StatusColor>('neutral');
  readonly dot = input(true);

  protected readonly textColor = computed(() => CSS_VARS[this.color()] ?? CSS_VARS.neutral);
  protected readonly bgColor = computed(() => `color-mix(in srgb, ${this.textColor()} 12%, transparent)`);
}
