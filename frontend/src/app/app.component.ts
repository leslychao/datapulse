import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ViewportGuardComponent } from './shared/layout/viewport-guard.component';

@Component({
  selector: 'dp-root',
  standalone: true,
  imports: [RouterOutlet, ViewportGuardComponent],
  template: `
    <dp-viewport-guard>
      <router-outlet />
    </dp-viewport-guard>
  `,
})
export class AppComponent {}
