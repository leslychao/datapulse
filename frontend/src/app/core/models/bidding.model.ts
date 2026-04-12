export type BiddingStrategyType = 'ECONOMY_HOLD' | 'MINIMAL_PRESENCE' | 'GROWTH' | 'POSITION_HOLD' | 'LAUNCH' | 'LIQUIDATION';
export type BidDecisionType = 'BID_UP' | 'BID_DOWN' | 'HOLD' | 'PAUSE' | 'RESUME' | 'SET_MINIMUM' | 'EMERGENCY_CUT';
export type BidPolicyStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
export type BiddingExecutionMode = 'RECOMMENDATION' | 'SEMI_AUTO' | 'FULL_AUTO';
export type BidActionStatus =
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'SCHEDULED'
  | 'EXECUTING'
  | 'RECONCILIATION_PENDING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'RETRY_SCHEDULED'
  | 'EXPIRED'
  | 'SUPERSEDED'
  | 'CANCELLED'
  | 'ON_HOLD';
export type BiddingRunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'PAUSED';

export interface BidPolicySummary {
  id: number;
  name: string;
  strategyType: BiddingStrategyType;
  executionMode: BiddingExecutionMode;
  status: BidPolicyStatus;
  assignmentCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface BidPolicyDetail extends BidPolicySummary {
  config: Record<string, unknown>;
  createdBy: number | null;
}

export interface CreateBidPolicyRequest {
  name: string;
  strategyType: BiddingStrategyType;
  executionMode: BiddingExecutionMode;
  config: Record<string, unknown>;
}

export interface UpdateBidPolicyRequest {
  name: string;
  executionMode: BiddingExecutionMode;
  config: Record<string, unknown>;
}

export interface BidPolicyAssignment {
  id: number;
  bidPolicyId: number;
  marketplaceOfferId: number | null;
  campaignExternalId: string | null;
  scope: string;
  createdAt: string;
}

export interface CreateBidAssignmentRequest {
  marketplaceOfferId?: number;
  campaignExternalId?: string;
  scope: string;
}

export interface BidDecisionSummary {
  id: number;
  marketplaceOfferId: number;
  strategyType: BiddingStrategyType;
  decisionType: BidDecisionType;
  currentBid: number | null;
  targetBid: number | null;
  explanationSummary: string | null;
  executionMode: BiddingExecutionMode;
  createdAt: string;
}

export interface BidDecisionDetail extends BidDecisionSummary {
  biddingRunId: number;
  signalSnapshot: Record<string, unknown> | null;
  guardsApplied: Record<string, unknown> | null;
}

export interface BiddingRunSummary {
  id: number;
  bidPolicyId: number;
  status: BiddingRunStatus;
  totalEligible: number;
  totalDecisions: number;
  totalBidUp: number;
  totalBidDown: number;
  totalHold: number;
  totalPause: number;
  startedAt: string;
  completedAt: string | null;
}

export interface ManualBidLock {
  id: number;
  marketplaceOfferId: number;
  lockedBid: number | null;
  reason: string | null;
  lockedBy: number | null;
  expiresAt: string | null;
  createdAt: string;
}

export interface CreateManualBidLockRequest {
  marketplaceOfferId: number;
  lockedBid?: number;
  reason?: string;
  expiresAt?: string;
}

export interface BidActionSummary {
  id: number;
  marketplaceOfferId: number;
  marketplaceType: string;
  decisionType: BidDecisionType;
  previousBid: number | null;
  targetBid: number;
  status: BidActionStatus;
  executionMode: BiddingExecutionMode;
  createdAt: string;
}

export interface BidPolicyFilter {
  status?: BidPolicyStatus[];
  strategyType?: BiddingStrategyType;
  executionMode?: BiddingExecutionMode[];
}

export interface BidDecisionFilter {
  bidPolicyId?: number;
  biddingRunId?: number;
  marketplaceOfferId?: number;
  decisionType?: BidDecisionType[];
  dateFrom?: string;
  dateTo?: string;
}

export interface BiddingDashboard {
  totalManagedProducts: number;
  activePolicies: number;
  productsByStrategy: Record<string, number>;
  decisionsByType: Record<string, number>;
  totalRunsLast7d: number;
  failedRunsLast7d: number;
  pausedRunsLast7d: number;
  topHighDrr: BiddingTopProductItem[];
  topImproved: BiddingTopProductItem[];
}

export interface BiddingTopProductItem {
  marketplaceOfferId: number;
  marketplaceSku: string;
  strategyType: BiddingStrategyType;
  lastDecisionType: BidDecisionType;
  currentBid: number | null;
  drrPct: number | null;
}
