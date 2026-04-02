import { MarketplaceType, Page } from './connection.model';
import { ExecutionMode } from './offer.model';

export type StrategyType = 'TARGET_MARGIN' | 'PRICE_CORRIDOR';
export type PolicyStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
export type PolicyExecutionMode = 'RECOMMENDATION' | 'SEMI_AUTO' | 'FULL_AUTO' | 'SIMULATED';
export type CommissionSource = 'AUTO' | 'MANUAL' | 'AUTO_WITH_MANUAL_FALLBACK';
export type RoundingDirection = 'FLOOR' | 'NEAREST' | 'CEIL';
export type RunTriggerType = 'POST_SYNC' | 'MANUAL' | 'SCHEDULED' | 'POLICY_CHANGE';
export type RunStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'COMPLETED_WITH_ERRORS' | 'FAILED';
export type DecisionOutcome = 'CHANGE' | 'SKIP' | 'HOLD';
export type AssignmentScopeType = 'CONNECTION' | 'CATEGORY' | 'SKU';

export interface TargetMarginParams {
  targetMarginPct: number;
  commissionSource: CommissionSource;
  commissionManualPct: number | null;
  commissionLookbackDays: number;
  commissionMinTransactions: number;
  logisticsSource: CommissionSource;
  logisticsManualAmount: number | null;
  includeReturnAdjustment: boolean;
  includeAdCost: boolean;
  roundingStep: number;
  roundingDirection: RoundingDirection;
}

export interface PriceCorridorParams {
  corridorMinPrice: number | null;
  corridorMaxPrice: number | null;
}

export interface GuardConfig {
  marginGuardEnabled: boolean;
  frequencyGuardEnabled: boolean;
  frequencyGuardHours: number;
  volatilityGuardEnabled: boolean;
  volatilityGuardReversals: number;
  volatilityGuardPeriodDays: number;
  promoGuardEnabled: boolean;
  stockOutGuardEnabled: boolean;
  staleDataGuardHours: number;
}

export interface PricingPolicySummary {
  id: number;
  name: string;
  strategyType: StrategyType;
  executionMode: PolicyExecutionMode;
  status: PolicyStatus;
  priority: number;
  version: number;
  assignmentsCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface PricingPolicy {
  id: number;
  name: string;
  strategyType: StrategyType;
  strategyParams: TargetMarginParams | PriceCorridorParams;
  executionMode: PolicyExecutionMode;
  status: PolicyStatus;
  priority: number;
  version: number;
  approvalTimeoutHours: number;
  minMarginPct: number | null;
  maxPriceChangePct: number | null;
  minPrice: number | null;
  maxPrice: number | null;
  guardConfig: GuardConfig;
  assignmentsCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePolicyRequest {
  name: string;
  strategyType: StrategyType;
  strategyParams: TargetMarginParams | PriceCorridorParams;
  executionMode: PolicyExecutionMode;
  priority: number;
  approvalTimeoutHours: number;
  minMarginPct: number | null;
  maxPriceChangePct: number | null;
  minPrice: number | null;
  maxPrice: number | null;
  guardConfig: GuardConfig;
  confirmFullAuto?: boolean;
}

export interface UpdatePolicyRequest extends CreatePolicyRequest {}

export interface PolicyAssignment {
  id: number;
  scopeType: AssignmentScopeType;
  connectionId: number;
  connectionName: string;
  marketplace: MarketplaceType;
  categoryId: number | null;
  categoryName: string | null;
  marketplaceOfferId: number | null;
  offerName: string | null;
  sellerSku: string | null;
}

export interface CreateAssignmentRequest {
  connectionId: number;
  scopeType: AssignmentScopeType;
  categoryId?: number;
  marketplaceOfferId?: number;
}

export interface PricingRunSummary {
  id: number;
  triggerType: RunTriggerType;
  connectionId: number;
  connectionName: string;
  marketplace: MarketplaceType;
  status: RunStatus;
  totalOffers: number;
  eligibleCount: number;
  changeCount: number;
  skipCount: number;
  holdCount: number;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export interface PricingRunDetail extends PricingRunSummary {
  errorDetails: string | null;
}

export interface PricingDecisionSummary {
  id: number;
  offerName: string;
  sellerSku: string;
  connectionName: string;
  decisionType: DecisionOutcome;
  currentPrice: number | null;
  targetPrice: number | null;
  changePct: number | null;
  policyName: string;
  strategyType: StrategyType;
  executionMode: ExecutionMode;
  pricingRunId: number;
  skipReason: string | null;
  createdAt: string;
}

export interface PricingDecisionDetail extends PricingDecisionSummary {
  offerId: number;
  signalSnapshot: Record<string, unknown> | null;
  constraintsApplied: ConstraintStep[] | null;
  guardsEvaluated: GuardEvaluation[] | null;
  explanationSummary: string | null;
  policySnapshot: Record<string, unknown> | null;
}

export interface ConstraintStep {
  name: string;
  priceBefore: number;
  priceAfter: number;
}

export interface GuardEvaluation {
  guardName: string;
  passed: boolean;
  details: string | null;
}

export interface ManualPriceLock {
  id: number;
  offerId: number;
  offerName: string;
  sellerSku: string;
  connectionId: number;
  connectionName: string;
  lockedPrice: number;
  reason: string | null;
  lockedByName: string;
  lockedAt: string;
  expiresAt: string | null;
}

export interface CreateLockRequest {
  marketplaceOfferId: number;
  lockedPrice: number;
  reason?: string;
  expiresAt?: string;
}

export interface ImpactPreviewSummary {
  totalOffers: number;
  eligibleCount: number;
  changeCount: number;
  skipCount: number;
  holdCount: number;
  avgPriceChangePct: number | null;
  maxPriceChangePct: number | null;
  minMarginAfter: number | null;
}

export interface ImpactPreviewOffer {
  offerName: string;
  sellerSku: string;
  currentPrice: number | null;
  targetPrice: number | null;
  changePct: number | null;
  changeAmount: number | null;
  decisionType: DecisionOutcome;
  skipReason: string | null;
}

export interface ImpactPreviewResponse {
  summary: ImpactPreviewSummary;
  offers: Page<ImpactPreviewOffer>;
}

export interface PricingFilter {
  status?: PolicyStatus[];
  strategyType?: StrategyType;
  executionMode?: PolicyExecutionMode[];
}

export interface PricingRunFilter {
  connectionId?: number;
  status?: RunStatus[];
  triggerType?: RunTriggerType[];
  from?: string;
  to?: string;
}

export interface PricingDecisionFilter {
  connectionId?: number;
  marketplaceOfferId?: number;
  decisionType?: DecisionOutcome[];
  pricingRunId?: number;
  executionMode?: ExecutionMode;
  from?: string;
  to?: string;
}

export interface PricingLockFilter {
  connectionId?: number;
  search?: string;
}