import { Component, Input } from '@angular/core';

@Component({
  selector: 'dp-status-badge',
  standalone: true,
  template: `
    <span
      class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium leading-4"
      [style.background-color]="bgColor"
      [style.color]="textColor"
    >
      @if (dot) {
        <span class="inline-block h-1.5 w-1.5 rounded-full" [style.background-color]="textColor"></span>
      }
      {{ label }}
    </span>
  `,
})
export class StatusBadgeComponent {
  @Input({ required: true }) label = '';
  @Input({ required: true }) color: 'success' | 'error' | 'warning' | 'info' | 'neutral' = 'neutral';
  @Input() dot = true;

  get bgColor(): string {
    return `color-mix(in srgb, ${this.cssVar} 12%, transparent)`;
  }

  get textColor(): string {
    return this.cssVar;
  }

  private get cssVar(): string {
    switch (this.color) {
      case 'success': return 'var(--status-success)';
      case 'error': return 'var(--status-error)';
      case 'warning': return 'var(--status-warning)';
      case 'info': return 'var(--status-info)';
      default: return 'var(--status-neutral)';
    }
  }
}
