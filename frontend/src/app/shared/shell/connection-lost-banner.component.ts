import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';
import { LucideAngularModule, WifiOff } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';

import { WebSocketService } from '@core/websocket/websocket.service';

@Component({
  selector: 'dp-connection-lost-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule, TranslatePipe],
  template: `
    @if (ws.wasConnected() && !ws.connected()) {
      <div
        class="flex items-center gap-2 bg-[var(--status-warning)] px-6 py-1.5 text-[length:var(--text-sm)] font-medium text-white"
        role="alert"
        aria-live="assertive"
      >
        <lucide-icon [img]="WifiOff" [size]="16" />
        <span>{{ 'connection_banner.lost' | translate }}</span>
      </div>
    }
  `,
})
export class ConnectionLostBannerComponent {
  protected readonly WifiOff = WifiOff;
  protected readonly ws = inject(WebSocketService);
}
