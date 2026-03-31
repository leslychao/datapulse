import { computed } from '@angular/core';
import { signalStore, withState, withComputed, withMethods, patchState } from '@ngrx/signals';
import { AppNotification } from '@core/models/notification.model';

export interface NotificationState {
  notifications: AppNotification[];
  unreadCount: number;
  loading: boolean;
}

const initialState: NotificationState = {
  notifications: [],
  unreadCount: 0,
  loading: false,
};

export const NotificationStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed((store) => ({
    hasUnread: computed(() => store.unreadCount() > 0),
    latestNotifications: computed(() => store.notifications().slice(0, 5)),
  })),
  withMethods((store) => ({
    setNotifications(notifications: AppNotification[]): void {
      patchState(store, { notifications });
    },
    addNotification(notification: AppNotification): void {
      patchState(store, {
        notifications: [notification, ...store.notifications()],
        unreadCount: store.unreadCount() + 1,
      });
    },
    setUnreadCount(count: number): void {
      patchState(store, { unreadCount: count });
    },
    decrementUnread(): void {
      patchState(store, { unreadCount: Math.max(0, store.unreadCount() - 1) });
    },
    markAsRead(id: number): void {
      patchState(store, {
        notifications: store.notifications().map((n) =>
          n.id === id ? { ...n, read: true } : n,
        ),
        unreadCount: Math.max(0, store.unreadCount() - 1),
      });
    },
    markAllRead(): void {
      patchState(store, {
        notifications: store.notifications().map((n) => ({ ...n, read: true })),
        unreadCount: 0,
      });
    },
    setLoading(loading: boolean): void {
      patchState(store, { loading });
    },
  })),
);
