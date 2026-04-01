export type QueueType = 'ATTENTION' | 'DECISION' | 'PROCESSING';

export type QueueItemStatus = 'PENDING' | 'IN_PROGRESS' | 'DONE' | 'DISMISSED';

export interface Queue {
  queueId: number;
  name: string;
  queueType: QueueType;
  isSystem: boolean;
  pendingCount: number;
  inProgressCount: number;
  totalActiveCount: number;
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
  connectionId?: number[];
  query?: string;
  severity?: string[];
  mismatchType?: string[];
}

export interface CreateQueueRequest {
  name: string;
  queueType: QueueType;
  autoCriteria: {
    entity_type: string;
    match_rules: QueueMatchRule[];
  } | null;
}

export interface QueueMatchRule {
  field: string;
  op: string;
  value: unknown;
}

export interface BulkActionResult {
  processed: number;
  failed: number;
  results: { actionId: number; status?: string; error?: string; currentStatus?: string }[];
}
