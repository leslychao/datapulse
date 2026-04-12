export type MarketplaceType = 'WB' | 'OZON' | 'YANDEX';
export type ConnectionStatus = 'PENDING_VALIDATION' | 'ACTIVE' | 'AUTH_FAILED' | 'DISABLED' | 'ARCHIVED';
export type SyncHealth = 'OK' | 'SYNCING' | 'STALE' | 'ERROR';

export interface ConnectionSummary {
  id: number;
  marketplaceType: MarketplaceType;
  name: string;
  status: ConnectionStatus;
  lastCheckAt: string | null;
  lastSuccessAt: string | null;
  lastErrorCode: string | null;
}

export interface ConnectionDetail {
  id: number;
  marketplaceType: MarketplaceType;
  name: string;
  status: ConnectionStatus;
  externalAccountId: string | null;
  lastCheckAt: string | null;
  lastSuccessAt: string | null;
  lastErrorAt: string | null;
  lastErrorCode: string | null;
  createdAt: string;
  updatedAt: string;
  syncStates: SyncState[];
}

export interface CreateConnectionRequest {
  marketplaceType: MarketplaceType;
  name: string;
  credentials: WbCredentials | OzonCredentials | YandexCredentials;
}

export interface WbCredentials {
  apiToken: string;
}

export interface OzonCredentials {
  clientId: string;
  apiKey: string;
}

export interface YandexCredentials {
  apiKey: string;
}

export interface UpdateCredentialsRequest {
  credentials: WbCredentials | OzonCredentials | YandexCredentials;
}

export interface SyncState {
  dataDomain: string;
  lastSyncAt: string | null;
  lastSuccessAt: string | null;
  nextScheduledAt: string | null;
  status: string;
}

export interface ConnectionSyncStatus {
  connectionId: number;
  connectionName: string;
  lastSuccessAt: string | null;
  status: SyncHealth;
}

/** WebSocket envelope for `/topic/workspace/{id}/sync-status` (backend `WorkspaceSyncStatusPush`). */
export type SyncStatusPushReason = 'STATE_CHANGED' | 'ETL_JOB_COMPLETED';

export interface WorkspaceSyncStatusPush {
  reason: SyncStatusPushReason;
  connection: ConnectionSyncStatus;
}

export interface CallLogEntry {
  id: number;
  endpoint: string;
  httpMethod: string;
  httpStatus: number | null;
  durationMs: number;
  requestSizeBytes: number | null;
  responseSizeBytes: number | null;
  correlationId: string;
  errorDetails: string | null;
  retryAttempt: number;
  createdAt: string;
}

export interface CallLogFilter {
  from?: string;
  to?: string;
  endpoint?: string;
  httpStatus?: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
