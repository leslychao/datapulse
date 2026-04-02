import { Injectable, inject, signal, NgZone } from '@angular/core';
import { RxStomp, RxStompConfig } from '@stomp/rx-stomp';
import { Subscription } from 'rxjs';
import { QueryClient } from '@tanstack/angular-query-experimental';

import { environment } from '@env';
import { NotificationApiService } from '@core/api/notification-api.service';
import { AppNotification, ConnectionSyncStatus, MismatchWsEvent } from '@core/models';
import { SyncStatusStore } from '@shared/stores/sync-status.store';
import { NotificationStore } from '@shared/stores/notification.store';

const INITIAL_RECONNECT_DELAY_MS = 1000;
const MAX_RECONNECT_DELAY_MS = 30000;

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private readonly zone = inject(NgZone);
  private readonly syncStore = inject(SyncStatusStore);
  private readonly notificationStore = inject(NotificationStore);
  private readonly notificationApi = inject(NotificationApiService);
  private readonly queryClient = inject(QueryClient);
  private rxStomp: RxStomp | null = null;
  private subscriptions: Subscription[] = [];
  private reconnectAttempts = 0;
  private lastMessageTimestamp: string | null = null;

  readonly connected = signal(false);
  readonly reconnecting = signal(false);
  readonly wasConnected = signal(false);
  readonly lastMismatchEvent = signal<MismatchWsEvent | null>(null);

  connect(workspaceId: number): void {
    this.disconnect();

    const config: RxStompConfig = {
      brokerURL: this.buildWsUrl(),
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: INITIAL_RECONNECT_DELAY_MS,
      beforeConnect: (client) => {
        client.stompClient.brokerURL = this.buildWsUrl();
        const delay = Math.min(
          INITIAL_RECONNECT_DELAY_MS * Math.pow(2, this.reconnectAttempts),
          MAX_RECONNECT_DELAY_MS,
        );
        client.stompClient.reconnectDelay = delay;
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
          this.wasConnected.set(true);
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
    this.wasConnected.set(false);
    this.reconnectAttempts = 0;
  }

  subscribeTo<T>(destination: string, callback: (msg: T) => void): Subscription | null {
    if (!this.rxStomp) return null;
    const sub = this.rxStomp.watch(destination).subscribe((message) => {
      this.zone.run(() => {
        const body = JSON.parse(message.body) as T;
        callback(body);
      });
    });
    this.subscriptions.push(sub);
    return sub;
  }

  subscribeToQueueItems(
    workspaceId: number,
    queueId: number,
    callback: (msg: { type: string; itemId?: number }) => void,
  ): Subscription | null {
    const dest = `/topic/workspace/${workspaceId}/queues/${queueId}`;
    return this.subscribeTo(dest, callback);
  }

  subscribeToAction(workspaceId: number, actionId: number): Subscription | null {
    const destination = `/topic/workspace/${workspaceId}/actions/${actionId}`;
    return this.subscribeTo(destination, () => {
      this.queryClient.invalidateQueries({ queryKey: ['action', actionId] });
    });
  }

  subscribeToWorkspace(workspaceId: number): void {
    const ws = `/topic/workspace/${workspaceId}`;

    this.subscribeTo<ConnectionSyncStatus>(`${ws}/sync-status`, (msg) => {
      this.syncStore.updateConnection(msg.connectionId, {
        connectionName: msg.connectionName,
        lastSuccessAt: msg.lastSuccessAt,
        status: msg.status,
      });
      this.syncStore.setLastUpdated(new Date().toISOString());
    });

    this.subscribeTo<AppNotification>(`/user/queue/notifications`, (msg) => {
      this.notificationStore.addNotification(msg);
      this.lastMessageTimestamp = new Date().toISOString();
    });

    this.subscribeTo(`${ws}/alerts`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['alerts'] });
      this.queryClient.invalidateQueries({ queryKey: ['alerts', 'blocker'] });
    });

    this.subscribeTo(`${ws}/actions`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['actions'] });
      this.queryClient.invalidateQueries({ queryKey: ['action'] });
      this.queryClient.invalidateQueries({ queryKey: ['actions-kpi'] });
    });

    this.subscribeTo(`${ws}/grid-updates`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['offers'] });
    });

    this.subscribeTo(`${ws}/action-updates`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['offers'] });
      this.queryClient.invalidateQueries({ queryKey: ['offer-detail'] });
      this.queryClient.invalidateQueries({ queryKey: ['action-history'] });
    });

    this.subscribeTo(`${ws}/kpi-updates`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['grid-kpi'] });
    });

    this.subscribeTo(`${ws}/pricing-runs`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['pricing-runs'] });
    });

    this.subscribeTo(`${ws}/pricing-decisions`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['pricing-decisions'] });
    });

    this.subscribeTo<MismatchWsEvent>(`${ws}/mismatches`, (msg) => {
      this.queryClient.invalidateQueries({ queryKey: ['mismatches'] });
      this.queryClient.invalidateQueries({ queryKey: ['mismatch-summary'] });
      this.queryClient.invalidateQueries({ queryKey: ['mismatch-detail'] });
      this.lastMismatchEvent.set(msg);
    });

    this.subscribeTo(`${ws}/promo-campaigns`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaigns'] });
    });

    this.subscribeTo(`${ws}/promo-actions`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['promo-actions'] });
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign'] });
    });

    this.subscribeTo(`${ws}/promo-evaluations`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['promo-evaluations'] });
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    });

    this.subscribeTo(`${ws}/promo-decisions`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['promo-decisions'] });
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    });

    this.subscribeTo(`${ws}/promo-policies`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['promo-policies'] });
      this.queryClient.invalidateQueries({ queryKey: ['promo-policy'] });
    });

    this.subscribeTo(`${ws}/queues`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['queues'] });
      this.queryClient.invalidateQueries({ queryKey: ['queue'] });
    });

    this.subscribeTo(`${ws}/queue-items`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['queueItems'] });
    });

    this.subscribeTo(`${ws}/analytics-updates`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['analytics'] });
    });

    this.subscribeTo(`${ws}/sync-completed`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['sync-status'] });
      this.queryClient.invalidateQueries({ queryKey: ['offers'] });
      this.queryClient.invalidateQueries({ queryKey: ['analytics'] });
    });

    this.subscribeTo(`${ws}/connection-updates`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['connections'] });
      this.queryClient.invalidateQueries({ queryKey: ['connection'] });
      this.queryClient.invalidateQueries({ queryKey: ['connection-sync-state'] });
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
    if (base.startsWith('ws')) {
      return base;
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}${base}`;
  }
}
