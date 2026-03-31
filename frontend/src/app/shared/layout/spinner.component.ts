import { Component, Input } from '@angular/core';

@Component({
  selector: 'dp-spinner',
  standalone: true,
  template: `
    <div
      class="animate-spin rounded-full border-2"
      [style.width.px]="size"
      [style.height.px]="size"
      [style.border-color]="'var(--border-default)'"
      [style.border-top-color]="color"
    ></div>
  `,
})
export class SpinnerComponent {
  @Input() size = 16;
  @Input() color = 'var(--accent-primary)';
}
