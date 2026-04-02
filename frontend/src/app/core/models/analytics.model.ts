export type Granularity = 'DAILY' | 'WEEKLY' | 'MONTHLY';
export type CogsStatus = 'OK' | 'NO_COST_PROFILE' | 'NO_SALES';
export type StockOutRisk = 'CRITICAL' | 'WARNING' | 'NORMAL';
export type SyncDomainStatus = 'FRESH' | 'STALE' | 'OVERDUE';

export interface AnalyticsFilter {
  connectionId?: number;
  from?: string;
  to?: string;
  period?: string;
  search?: string;
  granularity?: Granularity;
  sellerSkuId?: number;
  stockOutRisk?: StockOutRisk;
  productId?: number;
}

export interface CostBreakdownItem {
  category: string;
  amount: number;
  percent: number;
}

export interface PnlSummary {
  revenueAmount: number;
  totalCostsAmount: number;
  cogsAmount: number;
  advertisingCostAmount: number;
  marketplacePnl: number;
  fullPnl: number;
  reconciliationResidual: number;
  reconciliationRatio: number;
  revenueDeltaPct: number | null;
  costsDeltaPct: number | null;
  cogsDeltaPct: number | null;
  advertisingDeltaPct: number | null;
  pnlDeltaPct: number | null;
  costBreakdown: CostBreakdownItem[];
}

export interface PnlTrendPoint {
  period: string;
  revenueAmount: number;
  totalCostsAmount: number;
  cogsAmount: number;
  advertisingCostAmount: number;
  fullPnl: number;
}

export interface PnlByProduct {
  connectionId: number;
  sourcePlatform: string;
  sellerSkuId: number;
  productId: number;
  period: number;
  attributionLevel: string;
  skuCode: string;
  productName: string;
  revenueAmount: number;
  marketplaceCommissionAmount: number;
  acquiringCommissionAmount: number;
  logisticsCostAmount: number;
  storageCostAmount: number;
  penaltiesAmount: number;
  marketingCostAmount: number;
  acceptanceCostAmount: number;
  otherMarketplaceChargesAmount: number;
  compensationAmount: number;
  refundAmount: number;
  netPayout: number;
  grossCogs: number | null;
  netCogs: number | null;
  cogsStatus: CogsStatus;
  advertisingCost: number | null;
  marketplacePnl: number;
  fullPnl: number | null;
}

export interface PnlByPosting {
  postingId: string;
  connectionId: number;
  sourcePlatform: string;
  orderId: string | null;
  sellerSkuId: number | null;
  productId: number | null;
  skuCode: string;
  productName: string;
  financeDate: string;
  revenueAmount: number;
  marketplaceCommissionAmount: number;
  acquiringCommissionAmount: number;
  logisticsCostAmount: number;
  storageCostAmount: number;
  penaltiesAmount: number;
  marketingCostAmount: number;
  acceptanceCostAmount: number;
  otherMarketplaceChargesAmount: number;
  compensationAmount: number;
  refundAmount: number;
  netPayout: number;
  quantity: number | null;
  grossCogs: number | null;
  netCogs: number | null;
  cogsStatus: CogsStatus;
  reconciliationResidual: number;
}

export interface PostingEntry {
  entryId: number;
  entryType: string;
  attributionLevel: string;
  revenueAmount: number;
  marketplaceCommissionAmount: number;
  acquiringCommissionAmount: number;
  logisticsCostAmount: number;
  storageCostAmount: number;
  penaltiesAmount: number;
  acceptanceCostAmount: number;
  marketingCostAmount: number;
  otherMarketplaceChargesAmount: number;
  compensationAmount: number;
  refundAmount: number;
  netPayout: number;
  financeDate: string;
}

export interface PostingDetail {
  postingId: string;
  skuCode: string;
  productName: string;
  sourcePlatform: string;
  financeDate: string;
  revenueAmount: number;
  totalCostsAmount: number;
  netPayout: number;
  netCogs: number;
  reconciliationResidual: number;
  entries: PostingEntry[];
}

export interface InventoryOverview {
  totalSkus: number;
  criticalCount: number;
  warningCount: number;
  normalCount: number;
  frozenCapital: number;
  topCritical: InventoryByProduct[];
}

export interface InventoryByProduct {
  sellerSkuId: number;
  skuCode: string;
  productName: string;
  sourcePlatform: string;
  available: number;
  reserved: number;
  daysOfCover: number;
  stockOutRisk: StockOutRisk;
  frozenCapital: number | null;
  recommendedReplenishment: number;
  avgDailySales14d: number;
  costPrice: number | null;
}

export interface StockHistoryPoint {
  date: string;
  available: number;
  reserved: number;
}

export interface ReturnsSummary {
  returnRatePct: number;
  returnRateDeltaPct: number | null;
  totalRefundAmount: number;
  topReturnReason: string;
  reasonBreakdown: ReturnReasonItem[];
  penaltyBreakdown: PenaltyItem[];
  totalPenalties: number;
}

export interface ReturnReasonItem {
  reason: string;
  count: number;
  percent: number;
}

export interface PenaltyItem {
  type: string;
  amount: number;
}

export interface ReturnsByProduct {
  sellerSkuId: number;
  skuCode: string;
  productName: string;
  sourcePlatform: string;
  returnCount: number;
  returnQuantity: number;
  returnRatePct: number;
  financialRefundAmount: number;
  penaltiesAmount: number;
  topReturnReason: string;
  saleCount: number;
  saleQuantity: number;
}

export interface ReturnsTrendPoint {
  period: string;
  returnRatePct: number;
  returnQuantity: number;
}

export interface SyncDomainInfo {
  domain: string;
  lastSuccessAt: string | null;
  status: SyncDomainStatus;
  recordCount: number;
}

export interface ConnectionDataQuality {
  connectionId: number;
  connectionName: string;
  marketplaceType: string;
  automationBlocked: boolean;
  blockReason: string | null;
  lastSyncRelative: string | null;
  domains: SyncDomainInfo[];
}

export interface DataQualityStatus {
  connections: ConnectionDataQuality[];
}

export interface ReconciliationConnection {
  connectionId: number;
  connectionName: string;
  marketplaceType: string;
  residualAmount: number;
  residualRatioPct: number;
  baselineRatioPct: number;
  status: 'NORMAL' | 'ANOMALY' | 'INSUFFICIENT_DATA' | 'CALIBRATION';
}

export interface ReconciliationTrendPoint {
  period: string;
  connectionId: number;
  residualRatioPct: number;
  baselineRatioPct: number;
}

export interface ResidualBucket {
  label: string;
  count: number;
  from: number;
  to: number;
}

export interface ReconciliationResult {
  connections: ReconciliationConnection[];
  trend: ReconciliationTrendPoint[];
  distribution: ResidualBucket[];
}
