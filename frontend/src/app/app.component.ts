import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { TranslateService } from '@ngx-translate/core';

import { ViewportGuardComponent } from './shared/layout/viewport-guard.component';

@Component({
  selector: 'dp-root',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, ViewportGuardComponent],
  template: `
    <dp-viewport-guard>
      <router-outlet />
    </dp-viewport-guard>
  `,
})
export class AppComponent {
  constructor() {
    inject(TranslateService).use('ru');
  }
}
