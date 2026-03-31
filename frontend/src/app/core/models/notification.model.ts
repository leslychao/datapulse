export type NotificationSeverity = 'CRITICAL' | 'WARNING' | 'INFO';

export interface AppNotification {
  id: number;
  severity: NotificationSeverity;
  title: string;
  body: string;
  createdAt: string;
  read: boolean;
  entityType: string | null;
  entityId: number | null;
}

export interface UnreadCount {
  count: number;
}
