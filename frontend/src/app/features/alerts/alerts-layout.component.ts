import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'dp-alerts-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet],
  template: `
    <div class="flex h-full min-h-0 flex-col bg-[var(--bg-primary)]">
      <router-outlet />
    </div>
  `,
})
export class AlertsLayoutComponent {}
