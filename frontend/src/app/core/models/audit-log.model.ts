export type AuditActorType = 'USER' | 'SYSTEM' | 'SCHEDULER';

export type AuditOutcome = 'SUCCESS' | 'DENIED' | 'FAILED';

export interface AuditLogEntry {
  id: number;
  actorType: AuditActorType;
  actorUserId: number | null;
  actorName: string | null;
  actorEmail: string | null;
  actionType: string;
  entityType: string;
  entityId: string | null;
  outcome: AuditOutcome;
  ipAddress: string | null;
  details: Record<string, unknown> | null;
  createdAt: string;
}

export interface AuditLogFilter {
  userId?: number;
  actionType?: string;
  entityType?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface AuditLogPage {
  content: AuditLogEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
