import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'dp-page-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex h-full flex-col overflow-hidden">
      <ng-content select="[pageHeader]"></ng-content>
      <ng-content select="[pageToolbar]"></ng-content>
      <div class="flex-1 overflow-auto p-4">
        <ng-content></ng-content>
      </div>
    </div>
  `,
})
export class PageLayoutComponent {}
