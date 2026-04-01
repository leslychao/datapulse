import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'dp-spinner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center gap-2 py-8 text-sm text-[var(--text-secondary)]">
      <span
        class="dp-spinner inline-block rounded-full border-2 border-[var(--border-default)]"
        [style.width.px]="size()"
        [style.height.px]="size()"
        [style.border-top-color]="color()"
      ></span>
      @if (message()) {
        {{ message() }}
      }
    </div>
  `,
})
export class SpinnerComponent {
  readonly size = input(16);
  readonly color = input('var(--accent-primary)');
  readonly message = input('');
}
