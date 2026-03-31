import { Injectable, inject, signal, NgZone } from '@angular/core';
import { RxStomp, RxStompConfig } from '@stomp/rx-stomp';
import { Subscription } from 'rxjs';

import { environment } from '@env';
import { AuthService } from '@core/auth/auth.service';
import { NotificationApiService } from '@core/api/notification-api.service';
import { AppNotification } from '@core/models';
import { SyncStatusStore } from '@shared/stores/sync-status.store';
import { NotificationStore } from '@shared/stores/notification.store';

interface SyncStatusMessage {
  connectionId: number;
  connectionName: string;
  lastSuccessAt: string | null;
  status: 'OK' | 'STALE' | 'ERROR';
}

const INITIAL_RECONNECT_DELAY_MS = 1000;
const MAX_RECONNECT_DELAY_MS = 30000;

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private readonly authService = inject(AuthService);
  private readonly zone = inject(NgZone);
  private readonly syncStore = inject(SyncStatusStore);
  private readonly notificationStore = inject(NotificationStore);
  private readonly notificationApi = inject(NotificationApiService);
  private rxStomp: RxStomp | null = null;
  private subscriptions: Subscription[] = [];
  private reconnectAttempts = 0;
  private lastMessageTimestamp: string | null = null;

  readonly connected = signal(false);
  readonly reconnecting = signal(false);

  connect(workspaceId: number): void {
    this.disconnect();

    const config: RxStompConfig = {
      brokerURL: this.buildWsUrl(),
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: INITIAL_RECONNECT_DELAY_MS,
      beforeConnect: (client) => {
        client.brokerURL = this.buildWsUrl();
        const delay = Math.min(
          INITIAL_RECONNECT_DELAY_MS * Math.pow(2, this.reconnectAttempts),
          MAX_RECONNECT_DELAY_MS,
        );
        client.reconnectDelay = delay;
        this.reconnectAttempts++;
      },
    };

    this.rxStomp = new RxStomp();
    this.rxStomp.configure(config);

    this.rxStomp.connectionState$.subscribe((state) => {
      this.zone.run(() => {
        const wasReconnecting = this.reconnecting();
        this.connected.set(state === 1);
        this.reconnecting.set(state === 0 || state === 3);

        if (state === 1) {
          this.reconnectAttempts = 0;
          if (wasReconnecting) {
            this.syncMissedNotifications();
          }
        }
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
    this.reconnectAttempts = 0;
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
    this.subscribeTo(`/topic/workspace/${workspaceId}/alerts`, (_msg) => {
      // TODO: dispatch to alert store when alert dashboard is implemented (Phase B)
    });

    this.subscribeTo<SyncStatusMessage>(
      `/topic/workspace/${workspaceId}/sync-status`,
      (msg) => {
        this.syncStore.updateConnection(msg.connectionId, {
          connectionName: msg.connectionName,
          lastSuccessAt: msg.lastSuccessAt,
          status: msg.status,
        });
        this.syncStore.setLastUpdated(new Date().toISOString());
      },
    );

    this.subscribeTo(`/topic/workspace/${workspaceId}/actions`, (_msg) => {
      // TODO: dispatch to action store when execution module is implemented (Phase D)
    });

    this.subscribeTo<AppNotification>(`/user/queue/notifications`, (msg) => {
      this.notificationStore.addNotification(msg);
      this.lastMessageTimestamp = new Date().toISOString();
    });
  }

  private syncMissedNotifications(): void {
    if (!this.lastMessageTimestamp) return;
    this.notificationApi.list({ since: this.lastMessageTimestamp }).subscribe({
      next: (notifications) => {
        this.notificationStore.setNotifications(notifications);
        const unreadCount = notifications.filter((n) => !n.read).length;
        this.notificationStore.setUnreadCount(unreadCount);
      },
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
