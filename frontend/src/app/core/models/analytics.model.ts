export type Granularity = 'DAILY' | 'WEEKLY' | 'MONTHLY';
export type CogsStatus = 'OK' | 'NO_COST_PROFILE' | 'NO_SALES';
export type StockOutRisk = 'CRITICAL' | 'WARNING' | 'NORMAL';
export type SyncDomainStatus = 'FRESH' | 'STALE' | 'OVERDUE';

export interface AnalyticsFilter {
  from?: string;
  to?: string;
  period?: string;
  search?: string;
  granularity?: Granularity;
  sellerSkuId?: number;
  stockOutRisk?: string;
  productId?: number;
  sourcePlatform?: string;
}

export interface CostBreakdownItem {
  category: string;
  amount: number;
  percent: number;
}

export interface PnlSummary {
  revenueAmount: number;
  totalCostsAmount: number;
  compensationAmount: number;
  refundAmount: number;
  cogsAmount: number;
  advertisingCostAmount: number;
  marketplacePnl: number;
  fullPnl: number;
  reconciliationResidual: number;
  reconciliationRatio: number;
  revenueDeltaPct: number | null;
  costsDeltaPct: number | null;
  compensationDeltaPct: number | null;
  refundDeltaPct: number | null;
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
  sourcePlatform: string;
  productId: number;
  sellerSkuId: number;
  skuCode: string;
  productName: string;
  warehouseId: number | null;
  warehouseName: string | null;
  analysisDate: string;
  available: number;
  reserved: number | null;
  avgDailySales14d: number | null;
  daysOfCover: number | null;
  stockOutRisk: StockOutRisk;
  costPrice: number | null;
  frozenCapital: number | null;
  recommendedReplenishment: number | null;
}

export interface StockHistoryPoint {
  date: string;
  available: number;
  reserved: number | null;
  warehouseId: number | null;
  warehouseName: string | null;
}

export interface ReturnsSummary {
  returnRatePct: number;
  returnRateDeltaPct: number | null;
  totalReturnAmount: number;
  totalReturnCount: number;
  productsWithReturnsCount: number;
  topReturnReason: string;
  reasonBreakdown: ReturnReasonItem[];
}

export interface ReturnReasonItem {
  reason: string;
  count: number;
  percent: number;
  amount: number;
  productCount: number;
}

export interface ReturnsByProduct {
  sellerSkuId: number;
  skuCode: string;
  productName: string;
  sourcePlatform: string;
  returnCount: number;
  returnQuantity: number;
  returnAmount: number;
  returnRatePct: number;
  topReturnReason: string;
  distinctReasonCount: number;
  saleCount: number;
  saleQuantity: number;
}

export interface ReturnsTrendPoint {
  period: string;
  returnQuantity: number;
  saleQuantity: number;
  returnRatePct: number;
}

export interface ReturnReason {
  reason: string;
  returnCount: number;
  returnQuantity: number;
  returnAmount: number;
  percent: number;
  productCount: number;
}

export interface SyncDomainInfo {
  domain: string;
  lastSuccessAt: string | null;
  status: SyncDomainStatus;
}

export interface ConnectionDataQuality {
  connectionName: string;
  marketplaceType: string;
  automationBlocked: boolean;
  blockReason: string | null;
  blockReasonArgs: Record<string, unknown> | null;
  domains: SyncDomainInfo[];
}

export interface DataQualityStatus {
  connections: ConnectionDataQuality[];
}

export interface ReconciliationConnection {
  connectionName: string;
  marketplaceType: string;
  residualAmount: number;
  residualRatioPct: number;
  baselineRatioPct: number;
  status: 'NORMAL' | 'ANOMALY' | 'INSUFFICIENT_DATA' | 'CALIBRATION';
}

export interface ReconciliationTrendPoint {
  period: string;
  marketplaceType: string;
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

export type DataTrustLevel = 'trusted' | 'limited' | 'unreliable';
export type DomainSeverity = 'critical' | 'warning' | 'info';

export interface DomainImpact {
  severity: DomainSeverity;
  affectedAreasKey: string;
  reportLink: string | null;
}

export const DOMAIN_IMPACT: Record<string, DomainImpact> = {
  finance: { severity: 'critical', affectedAreasKey: 'analytics.data_quality.impact.finance', reportLink: 'pnl/summary' },
  orders: { severity: 'critical', affectedAreasKey: 'analytics.data_quality.impact.orders', reportLink: 'pnl/summary' },
  stock: { severity: 'warning', affectedAreasKey: 'analytics.data_quality.impact.stock', reportLink: 'inventory' },
  catalog: { severity: 'info', affectedAreasKey: 'analytics.data_quality.impact.catalog', reportLink: null },
  advertising: { severity: 'info', affectedAreasKey: 'analytics.data_quality.impact.advertising', reportLink: 'pnl/summary' },
  returns: { severity: 'warning', affectedAreasKey: 'analytics.data_quality.impact.returns', reportLink: 'returns/overview' },
};

export const SEVERITY_ORDER: Record<DomainSeverity, number> = {
  critical: 0,
  warning: 1,
  info: 2,
};
