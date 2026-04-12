import { MarketplaceType } from './connection.model';
import { EvaluationResult } from './offer.model';

export type CampaignStatus = 'UPCOMING' | 'ACTIVE' | 'ENDED';
export type ParticipationStatus =
  | 'ELIGIBLE'
  | 'PARTICIPATING'
  | 'DECLINED'
  | 'REMOVED'
  | 'BANNED'
  | 'AUTO_DECLINED';
export type PromoDecisionType = 'PARTICIPATE' | 'DECLINE' | 'DEACTIVATE' | 'PENDING_REVIEW';
export type PromoActionStatus =
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'EXECUTING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'EXPIRED'
  | 'CANCELLED';
export type PromoPolicyStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
export type ParticipationMode = 'RECOMMENDATION' | 'SEMI_AUTO' | 'FULL_AUTO' | 'SIMULATED';
export type PromoAssignmentScopeType = 'CONNECTION' | 'CATEGORY' | 'SKU';

export interface PromoCampaignSummary {
  id: number;
  promoName: string;
  sourcePlatform: MarketplaceType;
  promoType: string;
  mechanic: string;
  dateFrom: string;
  dateTo: string | null;
  freezeAt: string | null;
  eligibleCount: number;
  participatedCount: number;
  pendingReviewCount: number;
  status: CampaignStatus;
  connectionName: string;
}

export interface PromoCampaign extends PromoCampaignSummary {
  externalPromoId: string;
  description: string | null;
  participationDeadline: string | null;
  declinedCount: number;
  bannedCount: number;
  pendingReviewCount: number;
  succeededActionsCount: number;
  failedActionsCount: number;
}

export interface PromoProductSummary {
  id: number;
  productName: string;
  marketplaceSku: string;
  sellerSkuCode: string | null;
  requiredPrice: number | null;
  maxPromoPrice: number | null;
  currentPrice: number | null;
  discountPct: number | null;
  marginAtPromoPrice: number | null;
  stockAvailable: number | null;
  stockDaysOfCover: number | null;
  evaluationResult: EvaluationResult | null;
  decisionType: PromoDecisionType | null;
  participationStatus: ParticipationStatus;
  actionStatus: PromoActionStatus | null;
  actionId: number | null;
}

export interface PromoPolicySummary {
  id: number;
  name: string;
  status: PromoPolicyStatus;
  participationMode: ParticipationMode;
  minMarginPct: number;
  minStockDaysOfCover: number;
  maxPromoDiscountPct: number | null;
  version: number;
  assignmentCount: number;
  updatedAt: string;
  createdByName: string;
}

export interface PromoPolicy extends PromoPolicySummary {
  autoParticipateCategories: number[] | null;
  autoDeclineCategories: number[] | null;
  evaluationConfig: Record<string, unknown> | null;
  createdAt: string;
}

export interface CreatePromoPolicyRequest {
  name: string;
  participationMode: ParticipationMode;
  minMarginPct: number;
  minStockDaysOfCover: number;
  maxPromoDiscountPct: number | null;
  autoParticipateCategories: number[] | null;
  autoDeclineCategories: number[] | null;
  evaluationConfig: Record<string, unknown> | null;
}

export interface UpdatePromoPolicyRequest extends CreatePromoPolicyRequest {}

export interface PromoPolicyAssignment {
  id: number;
  connectionName: string;
  marketplace: MarketplaceType;
  scopeType: PromoAssignmentScopeType;
  scopeTargetName: string | null;
  categoryId: number | null;
  marketplaceOfferId: number | null;
}

export interface CreatePromoAssignmentRequest {
  sourcePlatform: string;
  scopeType: PromoAssignmentScopeType;
  categoryId?: number;
  marketplaceOfferId?: number;
}

export interface PromoEvaluation {
  id: number;
  campaignId: number;
  campaignName: string;
  sourcePlatform: MarketplaceType;
  productName: string;
  marketplaceSku: string;
  promoPrice: number | null;
  regularPrice: number | null;
  discountPct: number | null;
  cogs: number | null;
  marginAtPromoPrice: number | null;
  marginAtRegularPrice: number | null;
  marginDeltaPct: number | null;
  stockAvailable: number | null;
  stockDaysOfCover: number | null;
  stockSufficient: boolean;
  evaluationResult: EvaluationResult;
  skipReason: string | null;
  policyName: string;
  evaluatedAt: string;
}

export interface PromoDecision {
  id: number;
  campaignId: number;
  campaignName: string;
  sourcePlatform: MarketplaceType;
  productName: string;
  marketplaceSku: string;
  decisionType: PromoDecisionType;
  participationMode: ParticipationMode;
  targetPromoPrice: number | null;
  explanationSummary: string | null;
  decidedByName: string | null;
  policyName: string;
  policyVersion: number;
  createdAt: string;
}

export interface PromoDecisionDetail extends PromoDecision {
  policySnapshot: Record<string, unknown> | null;
  signalSnapshot: Record<string, unknown> | null;
  evaluationId: number | null;
  actionId: number | null;
  actionStatus: PromoActionStatus | null;
}

export interface PromoCampaignFilter {
  sourcePlatform?: string;
  marketplaceType?: MarketplaceType[];
  status?: CampaignStatus[];
  promoType?: string;
  from?: string;
  to?: string;
  search?: string;
}

export interface PromoProductFilter {
  participationStatus?: ParticipationStatus[];
  evaluationResult?: EvaluationResult[];
  decisionType?: PromoDecisionType[];
  actionStatus?: PromoActionStatus[];
  search?: string;
}

export interface PromoPolicyFilter {
  status?: PromoPolicyStatus[];
  participationMode?: ParticipationMode[];
  search?: string;
}

export interface PromoEvaluationFilter {
  campaignId?: number;
  sourcePlatform?: string;
  marketplaceType?: MarketplaceType[];
  evaluationResult?: EvaluationResult[];
  from?: string;
  to?: string;
  search?: string;
}

export type PromoActionType = 'ACTIVATE' | 'DEACTIVATE';

export interface PromoAction {
  id: number;
  campaignId: number;
  campaignName: string;
  productName: string;
  marketplaceSku: string;
  actionType: PromoActionType;
  status: PromoActionStatus;
  executionMode: 'LIVE' | 'SIMULATED';
  targetPromoPrice: number | null;
  attemptCount: number;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PromoActionFilter {
  campaignId?: number;
  status?: PromoActionStatus[];
  actionType?: PromoActionType[];
  search?: string;
}

export interface PromoDecisionFilter {
  campaignId?: number;
  sourcePlatform?: string;
  decisionType?: PromoDecisionType[];
  decidedBy?: 'all' | 'auto' | 'manual';
  from?: string;
  to?: string;
  search?: string;
}

export interface PromoCampaignKpi {
  activeCount: number;
  upcomingCount: number;
  productsParticipating: number;
}

export interface PromoEvaluationKpi {
  total: number;
  profitableCount: number;
  marginalCount: number;
  unprofitableCount: number;
}

export interface PromoDecisionKpi {
  participateCount: number;
  declineCount: number;
  pendingReviewCount: number;
}
