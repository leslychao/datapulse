import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'dp-centered-content',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex min-h-[calc(100vh-40px)] items-start justify-center pb-8 pt-[15vh]">
      <div class="w-full px-4" [style.max-width]="maxWidth()">
        <ng-content />
      </div>
    </div>
  `,
})
export class CenteredContentComponent {
  readonly maxWidth = input('640px');
}
