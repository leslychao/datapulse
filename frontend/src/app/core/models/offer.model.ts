import { MarketplaceType } from './connection.model';

export type OfferStatus = 'ACTIVE' | 'ARCHIVED' | 'BLOCKED' | 'INACTIVE';
export type StockRisk = 'CRITICAL' | 'WARNING' | 'NORMAL';
export type DataFreshness = 'FRESH' | 'STALE';
export type DecisionType = 'CHANGE' | 'SKIP' | 'HOLD';
export type PromoStatus = 'PARTICIPATING' | 'ELIGIBLE' | null;

/** Promo block on offer detail API ({@code OfferDetailResponse.PromoInfo}). */
export interface OfferPromoDetail {
  participating: boolean;
  campaignName: string | null;
  promoPrice: number | null;
  endsAt: string | null;
}

export type ActionStatus =
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'SCHEDULED'
  | 'EXECUTING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'ON_HOLD'
  | 'EXPIRED'
  | 'CANCELLED'
  | 'SUPERSEDED'
  | 'RETRY_SCHEDULED'
  | 'RECONCILIATION_PENDING'
  | 'REJECTED'
  | 'IN_PROGRESS';

export interface OfferSummary {
  offerId: number;
  sellerSkuId: number;
  skuCode: string;
  productName: string;
  marketplaceType: MarketplaceType;
  connectionName: string;
  status: OfferStatus;
  category: string | null;
  currentPrice: number | null;
  discountPrice: number | null;
  costPrice: number | null;
  marginPct: number | null;
  availableStock: number | null;
  daysOfCover: number | null;
  stockRisk: StockRisk | null;
  revenue30d: number | null;
  netPnl30d: number | null;
  velocity14d: number | null;
  returnRatePct: number | null;
  activePolicy: string | null;
  lastDecision: DecisionType | null;
  lastActionStatus: ActionStatus | null;
  pendingActionId: number | null;
  promoStatus: PromoStatus;
  manualLock: boolean;
  simulatedPrice: number | null;
  simulatedDeltaPct: number | null;
  lastSyncAt: string | null;
  dataFreshness: DataFreshness | null;
  bidPolicyName: string | null;
  bidStrategyType: string | null;
  currentBid: number | null;
  lastBidDecisionType: string | null;
  bidDrrPct: number | null;
  manualBidLock: boolean;
  bidLockId: number | null;
}

export interface OfferDetail extends Omit<OfferSummary, 'promoStatus'> {
  promoStatus: OfferPromoDetail | null;
  categoryId: number | null;
  brand: string | null;
  lockedPrice: number | null;
  lockReason: string | null;
  lockExpiresAt: string | null;
  policyName: string | null;
  policyStrategy: string | null;
  policyMode: string | null;
  lastDecisionDate: string | null;
  lastDecisionExplanation: string | null;
  lastActionDate: string | null;
  lastActionMode: string | null;
  warehouses: WarehouseStock[];
}

export interface WarehouseStock {
  warehouseName: string;
  available: number;
  reserved: number;
  daysOfCover: number | null;
}

export interface OfferFilter {
  marketplaceType?: MarketplaceType[];
  categoryId?: number[];
  status?: OfferStatus[];
  skuCode?: string;
  productName?: string;
  marginMin?: number;
  marginMax?: number;
  stockRisk?: StockRisk[];
  hasManualLock?: boolean;
  hasActivePromo?: boolean;
  lastDecision?: DecisionType[];
  lastActionStatus?: ActionStatus[];
}

export interface GridKpi {
  totalOffers: number;
  avgMarginPct: number | null;
  avgMarginTrend: number | null;
  pendingActionsCount: number;
  criticalStockCount: number;
  revenue30dTotal: number | null;
  revenue30dTrend: number | null;
}

export interface GridView {
  id: number;
  name: string;
  filters: OfferFilter;
  sortColumn: string | null;
  sortDirection: 'ASC' | 'DESC' | null;
  visibleColumns: string[] | null;
  groupBySku: boolean;
  isDefault: boolean;
  isSystem: boolean;
}

export interface CreateViewRequest {
  name: string;
  filters: OfferFilter;
  sortColumn?: string;
  sortDirection?: 'ASC' | 'DESC';
  visibleColumns?: string[];
  isDefault?: boolean;
}

export interface UpdateViewRequest extends CreateViewRequest {
  id: number;
}

export type EvaluationResult = 'PROFITABLE' | 'MARGINAL' | 'UNPROFITABLE' | 'INSUFFICIENT_STOCK' | 'INSUFFICIENT_DATA';
export type ParticipationDecision = 'PARTICIPATE' | 'DECLINE' | 'PENDING_REVIEW';
export type ExecutionMode = 'LIVE' | 'SIMULATED';

export interface PriceJournalEntry {
  id: number;
  decisionDate: string;
  decisionType: DecisionType;
  currentPrice: number | null;
  targetPrice: number | null;
  actualPrice: number | null;
  policyName: string | null;
  policyVersion: number | null;
  actionStatus: ActionStatus | null;
  executionMode: ExecutionMode | null;
  explanationSummary: string | null;
  reconciliationSource: string | null;
}

export interface PromoJournalEntry {
  id: number;
  promoName: string;
  promoType: string | null;
  periodStart: string;
  periodEnd: string;
  participationDecision: ParticipationDecision;
  evaluationResult: EvaluationResult | null;
  requiredPrice: number | null;
  marginAtPromoPrice: number | null;
  marginDeltaPct: number | null;
  actionStatus: ActionStatus | null;
  explanationSummary: string | null;
}

export interface ActionHistoryEntry {
  id: number;
  actionDate: string;
  actionType: string;
  status: ActionStatus;
  targetPrice: number | null;
  actualPrice: number | null;
  executionMode: ExecutionMode | null;
  reason: string | null;
  initiatedBy: string | null;
}

export interface BulkActionResponse {
  pricingRunId?: number;
  processed: number;
  skipped: number;
  errored: number;
  errors: string[];
}

export interface DraftPriceChange {
  offerId: number;
  newPrice: number;
  originalPrice: number;
  costPrice: number | null;
}

export interface BulkManualPreviewRequest {
  changes: { marketplaceOfferId: number; targetPrice: number }[];
}

export interface BulkManualPreviewSummary {
  totalRequested: number;
  willChange: number;
  willSkip: number;
  willBlock: number;
  avgChangePct: number;
  minMarginAfter: number | null;
  maxChangePct: number;
}

export interface BulkManualPreviewOffer {
  marketplaceOfferId: number;
  skuCode: string;
  productName: string;
  currentPrice: number;
  requestedPrice: number;
  effectivePrice: number;
  result: 'CHANGE' | 'SKIP';
  constraintsApplied: { name: string; fromPrice: number; toPrice: number }[];
  projectedMarginPct: number | null;
  skipReason: string | null;
  guard: string | null;
}

export interface BulkManualPreviewResponse {
  summary: BulkManualPreviewSummary;
  offers: BulkManualPreviewOffer[];
}

export interface LockPriceRequest {
  lockedPrice: number;
  reason?: string;
  expiresAt?: string;
}
