export type MarketplaceType = 'WB' | 'OZON';
export type ConnectionStatus = 'PENDING_VALIDATION' | 'ACTIVE' | 'AUTH_FAILED' | 'SUSPENDED' | 'DISABLED' | 'ARCHIVED' | 'ERROR';

export interface ConnectionSummary {
  id: number;
  marketplaceType: string;
  name: string;
  status: string;
  lastCheckAt: string | null;
  lastSuccessAt: string | null;
  lastErrorCode: string | null;
}

export interface ConnectionDetail {
  id: number;
  name: string;
  marketplaceType: MarketplaceType;
  status: ConnectionStatus;
  lastSuccessAt: string | null;
  lastErrorCode: string | null;
}

export interface CreateConnectionRequest {
  marketplaceType: MarketplaceType;
  name: string;
  credentials: WbCredentials | OzonCredentials;
}

export interface WbCredentials {
  apiToken: string;
}

export interface OzonCredentials {
  clientId: string;
  apiKey: string;
}

export interface UpdateCredentialsRequest {
  credentials: WbCredentials | OzonCredentials;
}

export interface SyncState {
  dataDomain: string;
  lastSyncAt: string | null;
  lastSuccessAt: string | null;
  nextScheduledAt: string | null;
  status: string;
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
