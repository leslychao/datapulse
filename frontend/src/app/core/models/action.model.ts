import { MarketplaceType } from './connection.model';
import { ExecutionMode } from './offer.model';

export type ActionType = 'SET_PRICE' | 'UPDATE_STOCK' | 'JOIN_PROMO' | 'LEAVE_PROMO';
export type AttemptOutcome = 'SUCCESS' | 'RETRY' | 'FAILURE' | 'INDETERMINATE';
export type ReconciliationSource = 'IMMEDIATE' | 'DEFERRED' | 'MANUAL' | null;
export type ErrorClassification = 'RATE_LIMIT' | 'TRANSIENT' | 'TIMEOUT' | 'NON_RECOVERABLE' | null;

export interface ActionSummary {
  id: number;
  offerName: string;
  sku: string;
  marketplace: MarketplaceType;
  connectionName: string;
  status: string;
  executionMode: ExecutionMode;
  targetPrice: number;
  currentPriceAtCreation: number;
  priceDeltaPct: number;
  attemptCount: number;
  maxAttempts: number;
  createdAt: string;
  updatedAt: string;
  initiatedBy: string | null;
}

export interface ActionAttempt {
  attemptNumber: number;
  startedAt: string;
  completedAt: string | null;
  outcome: AttemptOutcome;
  errorClassification: ErrorClassification;
  errorMessage: string | null;
  reconciliationSource: ReconciliationSource;
  reconciliationReadAt: string | null;
  actualPrice: number | null;
  priceMatch: boolean | null;
  providerRequest: string | null;
  providerResponse: string | null;
}

export interface ActionStateTransition {
  fromStatus: string;
  toStatus: string;
  timestamp: string;
  actor: string | null;
  reason: string | null;
}

export interface ActionDetail {
  id: number;
  type: ActionType;
  offerName: string;
  offerId: number;
  sku: string;
  marketplace: MarketplaceType;
  connectionName: string;
  status: string;
  executionMode: ExecutionMode;
  targetPrice: number;
  currentPriceAtCreation: number;
  priceDeltaPct: number;
  attemptCount: number;
  maxAttempts: number;
  createdAt: string;
  updatedAt: string;
  initiatedBy: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  holdReason: string | null;
  cancelReason: string | null;
  lastErrorMessage: string | null;
  supersedingActionId: number | null;
  stateHistory: ActionStateTransition[];
  attempts: ActionAttempt[];
}

export interface ActionFilter {
  connectionId?: number;
  status?: string[];
  executionMode?: ExecutionMode;
  search?: string;
  from?: string;
  to?: string;
}

export interface BulkApproveRequest {
  actionIds: number[];
}

export interface BulkApproveResponse {
  approved: number;
  failed: number;
  failures: { actionId: number; reason: string; currentStatus: string }[];
}
