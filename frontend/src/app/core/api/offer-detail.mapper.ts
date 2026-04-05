import {
  ActionStatus,
  DataFreshness,
  DecisionType,
  MarketplaceType,
  OfferDetail,
  OfferPromoDetail,
  OfferStatus,
  StockRisk,
} from '@core/models';

/**
 * JSON shape of {@code OfferDetailResponse} from seller-ops (nested policy / decision / action / promo / lock).
 */
export interface OfferDetailApiJson {
  offerId: number;
  skuCode: string;
  productName: string;
  marketplaceType: string;
  connectionName: string;
  status: string;
  category: string | null;
  currentPrice: number | null;
  discountPrice: number | null;
  costPrice: number | null;
  marginPct: number | null;
  availableStock: number | null;
  daysOfCover: number | null;
  stockRisk: string | null;
  revenue30d: number | null;
  netPnl30d: number | null;
  velocity14d: number | null;
  returnRatePct: number | null;
  activePolicy: {
    policyId: number;
    name: string;
    strategyType: string;
    executionMode: string;
  } | null;
  lastDecision: {
    decisionId: number;
    decisionType: string;
    currentPrice: number | null;
    targetPrice: number | null;
    explanationSummary: string | null;
    createdAt: string;
  } | null;
  lastAction: {
    actionId: number;
    status: string;
    targetPrice: number | null;
    executionMode: string | null;
    createdAt: string;
  } | null;
  promoStatus: OfferPromoDetail | null;
  manualLock: {
    lockedPrice: number | null;
    reason: string | null;
    lockedAt: string;
  } | null;
  simulatedPrice: number | null;
  simulatedDeltaPct: number | null;
  lastSyncAt: string | null;
  dataFreshness: string | null;
}

function asOfferStatus(value: string): OfferStatus {
  if (
    value === 'ACTIVE'
    || value === 'ARCHIVED'
    || value === 'BLOCKED'
    || value === 'INACTIVE'
  ) {
    return value;
  }
  return 'ACTIVE';
}

function asDecisionType(value: string | null | undefined): DecisionType | null {
  if (value === 'CHANGE' || value === 'SKIP' || value === 'HOLD') {
    return value;
  }
  return null;
}

function asActionStatus(value: string | null | undefined): ActionStatus | null {
  if (
    value === 'PENDING_APPROVAL'
    || value === 'APPROVED'
    || value === 'SCHEDULED'
    || value === 'EXECUTING'
    || value === 'SUCCEEDED'
    || value === 'FAILED'
    || value === 'ON_HOLD'
    || value === 'EXPIRED'
    || value === 'CANCELLED'
    || value === 'SUPERSEDED'
    || value === 'RETRY_SCHEDULED'
    || value === 'RECONCILIATION_PENDING'
    || value === 'REJECTED'
    || value === 'IN_PROGRESS'
  ) {
    return value;
  }
  return null;
}

function asStockRisk(value: string | null | undefined): StockRisk | null {
  if (value === 'CRITICAL' || value === 'WARNING' || value === 'NORMAL') {
    return value;
  }
  return null;
}

function asDataFreshness(value: string | null | undefined): DataFreshness | null {
  if (value === 'FRESH' || value === 'STALE') {
    return value;
  }
  return null;
}

export function mapOfferDetailApiResponse(raw: OfferDetailApiJson): OfferDetail {
  const policy = raw.activePolicy;
  const decision = raw.lastDecision;
  const action = raw.lastAction;
  const lock = raw.manualLock;

  return {
    offerId: raw.offerId,
    skuCode: raw.skuCode,
    productName: raw.productName,
    marketplaceType: raw.marketplaceType as MarketplaceType,
    connectionId: 0,
    connectionName: raw.connectionName,
    status: asOfferStatus(raw.status),
    category: raw.category,
    currentPrice: raw.currentPrice,
    discountPrice: raw.discountPrice,
    costPrice: raw.costPrice,
    marginPct: raw.marginPct,
    availableStock: raw.availableStock,
    daysOfCover: raw.daysOfCover,
    stockRisk: asStockRisk(raw.stockRisk),
    revenue30d: raw.revenue30d,
    netPnl30d: raw.netPnl30d,
    velocity14d: raw.velocity14d,
    returnRatePct: raw.returnRatePct,
    activePolicy: policy?.name ?? null,
    lastDecision: asDecisionType(decision?.decisionType),
    lastActionStatus: asActionStatus(action?.status),
    promoStatus: raw.promoStatus,
    manualLock: lock != null,
    simulatedPrice: raw.simulatedPrice,
    simulatedDeltaPct: raw.simulatedDeltaPct,
    lastSyncAt: raw.lastSyncAt,
    dataFreshness: asDataFreshness(raw.dataFreshness),
    categoryId: null,
    brand: null,
    lockedPrice: lock?.lockedPrice ?? null,
    lockReason: lock?.reason ?? null,
    lockExpiresAt: null,
    policyName: policy?.name ?? null,
    policyStrategy: policy?.strategyType ?? null,
    policyMode: policy?.executionMode ?? null,
    lastDecisionDate: decision?.createdAt ?? null,
    lastDecisionExplanation: decision?.explanationSummary ?? null,
    lastActionDate: action?.createdAt ?? null,
    lastActionMode: action?.executionMode ?? null,
    warehouses: [],
  };
}
