import { MarketplaceType } from './connection.model';

export type OfferStatus = 'ACTIVE' | 'ARCHIVED' | 'BLOCKED';
export type StockRisk = 'CRITICAL' | 'WARNING' | 'NORMAL';
export type DataFreshness = 'FRESH' | 'STALE';
export type DecisionType = 'CHANGE' | 'SKIP' | 'HOLD';
export type PromoStatus = 'PARTICIPATING' | 'ELIGIBLE' | null;

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
  | 'RECONCILIATION_PENDING';

export interface OfferSummary {
  id: number;
  skuCode: string;
  productName: string;
  marketplaceType: MarketplaceType;
  connectionId: number;
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
  promoStatus: PromoStatus;
  manualLock: boolean;
  simulatedPrice: number | null;
  simulatedDeltaPct: number | null;
  lastSyncAt: string | null;
  dataFreshness: DataFreshness | null;
}

export interface OfferDetail extends OfferSummary {
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
  promoName: string | null;
  promoPrice: number | null;
  promoEndDate: string | null;
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
  connectionId?: number[];
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

export interface BulkActionRequest {
  actionIds: number[];
}

export interface BulkActionResponse {
  processed: number;
  skipped: number;
  errored: number;
  errors: string[];
}

export interface DraftPriceChange {
  offerId: number;
  newPrice: number;
  originalPrice: number;
}

export interface BulkManualPreviewRequest {
  items: DraftPriceChange[];
}

export interface BulkManualPreviewItem {
  offerId: number;
  skuCode: string;
  productName: string;
  currentPrice: number;
  targetPrice: number;
  deltaPct: number;
  currentMargin: number | null;
  projectedMargin: number | null;
  status: 'CHANGE' | 'SKIP';
  skipReason: string | null;
}

export interface BulkManualPreviewResponse {
  items: BulkManualPreviewItem[];
  totalChange: number;
  totalSkip: number;
  avgDeltaPct: number;
  minMargin: number | null;
  maxDeltaPct: number;
}

export interface LockPriceRequest {
  lockedPrice: number;
  reason?: string;
  expiresAt?: string;
}
