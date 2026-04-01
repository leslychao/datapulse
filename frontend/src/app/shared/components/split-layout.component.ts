import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'dp-split-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex h-full overflow-hidden">
      <div class="flex-1 overflow-auto">
        <ng-content select="[main]"></ng-content>
      </div>
      @if (sideOpen()) {
        <div
          class="shrink-0 overflow-auto border-l border-[var(--border-default)]"
          [style.width.px]="sideWidth()"
        >
          <ng-content select="[side]"></ng-content>
        </div>
      }
    </div>
  `,
})
export class SplitLayoutComponent {
  readonly sideOpen = input(false);
  readonly sideWidth = input(400);
}
