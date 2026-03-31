import { Injectable, inject, signal, NgZone } from '@angular/core';
import { RxStomp, RxStompConfig } from '@stomp/rx-stomp';
import { Subscription } from 'rxjs';

import { environment } from '@env';
import { AuthService } from '@core/auth/auth.service';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private readonly authService = inject(AuthService);
  private readonly zone = inject(NgZone);
  private rxStomp: RxStomp | null = null;
  private subscriptions: Subscription[] = [];

  readonly connected = signal(false);
  readonly reconnecting = signal(false);

  connect(workspaceId: number): void {
    this.disconnect();

    const config: RxStompConfig = {
      brokerURL: this.buildWsUrl(),
      connectHeaders: {
        Authorization: `Bearer ${this.authService.accessToken}`,
      },
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 1000,
      beforeConnect: (client) => {
        client.configure({
          connectHeaders: {
            Authorization: `Bearer ${this.authService.accessToken}`,
          },
        });
      },
    };

    this.rxStomp = new RxStomp();
    this.rxStomp.configure(config);

    this.rxStomp.connectionState$.subscribe((state) => {
      this.zone.run(() => {
        // RxStompState: 0=CONNECTING, 1=OPEN, 2=CLOSING, 3=CLOSED
        this.connected.set(state === 1);
        this.reconnecting.set(state === 0 || state === 3);
      });
    });

    this.rxStomp.activate();
  }

  disconnect(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
    this.subscriptions = [];
    if (this.rxStomp) {
      this.rxStomp.deactivate();
      this.rxStomp = null;
    }
    this.connected.set(false);
    this.reconnecting.set(false);
  }

  subscribeTo<T>(destination: string, callback: (msg: T) => void): void {
    if (!this.rxStomp) return;
    const sub = this.rxStomp.watch(destination).subscribe((message) => {
      this.zone.run(() => {
        const body = JSON.parse(message.body) as T;
        callback(body);
      });
    });
    this.subscriptions.push(sub);
  }

  subscribeToWorkspace(workspaceId: number): void {
    this.subscribeTo(`/topic/workspace/${workspaceId}/alerts`, (msg) => {
      // dispatch to notification store
    });
    this.subscribeTo(`/topic/workspace/${workspaceId}/sync-status`, (msg) => {
      // dispatch to sync status store
    });
    this.subscribeTo(`/topic/workspace/${workspaceId}/actions`, (msg) => {
      // dispatch to relevant store
    });
    this.subscribeTo(`/user/queue/notifications`, (msg) => {
      // dispatch to notification store
    });
  }

  private buildWsUrl(): string {
    const base = environment.wsUrl;
    const token = this.authService.accessToken;
    if (base.startsWith('ws')) {
      return `${base}?token=${token}`;
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}${base}?token=${token}`;
  }
}
