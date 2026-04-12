export type QueueType = 'ATTENTION' | 'DECISION' | 'PROCESSING';

export type QueueItemStatus = 'PENDING' | 'IN_PROGRESS' | 'DONE' | 'DISMISSED';

export type SystemQueueCode =
  | 'PENDING_APPROVAL'
  | 'EXECUTION_ERRORS'
  | 'MISMATCHES'
  | 'NO_COST'
  | 'CRITICAL_STOCK'
  | 'RECENT_DECISIONS';

export interface Queue {
  queueId: number;
  name: string;
  queueType: QueueType;
  isSystem: boolean;
  systemCode: SystemQueueCode | null;
  pendingCount: number;
  inProgressCount: number;
  totalActiveCount: number;
  /** Present when API returns queue definition details (list/get). */
  autoCriteria?: QueueAutoCriteria | null;
}

export interface QueueItem {
  itemId: number;
  queueId: number;
  entityType: string;
  entityId: number;
  status: QueueItemStatus;
  assignedTo: string | null;
  entitySummary: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface QueueFilter {
  status?: QueueItemStatus[];
  assignedToMe?: boolean;
  marketplaceType?: string[];
  query?: string;
  severity?: string[];
  mismatchType?: string[];
  decisionType?: string[];
  actionStatus?: string[];
}

export interface CreateQueueRequest {
  name: string;
  queueType: QueueType;
  autoCriteria: QueueAutoCriteria | null;
}

export interface QueueAutoCriteria {
  entity_type: string;
  match_rules: QueueMatchRule[];
}

export interface QueueMatchRule {
  field: string;
  op: string;
  value: unknown;
}

export interface UpdateQueueRequest {
  name: string;
  autoCriteria: QueueAutoCriteria | null;
  enabled: boolean;
}