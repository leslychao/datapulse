export type NotificationSeverity = 'CRITICAL' | 'WARNING' | 'INFO';

export type NotificationType = 'ALERT' | 'APPROVAL_REQUEST' | 'SYNC_COMPLETED' | 'ACTION_FAILED';

export interface AppNotification {
  id: number;
  notificationType: NotificationType;
  alertEventId: number | null;
  severity: NotificationSeverity;
  title: string;
  body: string;
  createdAt: string;
  read: boolean;
}

export interface UnreadCount {
  count: number;
}
