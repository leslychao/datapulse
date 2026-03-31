export type MarketplaceType = 'WB' | 'OZON';
export type ConnectionStatus = 'PENDING_VALIDATION' | 'ACTIVE' | 'AUTH_FAILED' | 'SUSPENDED' | 'ERROR';

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
