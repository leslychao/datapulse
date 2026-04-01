export type MismatchType = 'PRICE' | 'STOCK' | 'PROMO' | 'FINANCE';

export type MismatchSeverity = 'WARNING' | 'CRITICAL';

export type MismatchStatus = 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED' | 'AUTO_RESOLVED' | 'IGNORED';

export type MismatchResolution = 'ACCEPTED' | 'REPRICED' | 'INVESTIGATED' | 'EXTERNAL' | 'IGNORED' | 'AUTO_RESOLVED';

export interface Mismatch {
  mismatchId: number;
  type: MismatchType;
  severity: MismatchSeverity;
  offerId: number;
  offerName: string;
  skuCode: string;
  marketplaceType: string;
  connectionName: string;
  expectedValue: string;
  actualValue: string;
  deltaPct: number | null;
  status: MismatchStatus;
  resolution: MismatchResolution | null;
  resolvedAt: string | null;
  detectedAt: string;
  relatedActionId: number | null;
}

export interface MismatchDetail extends Mismatch {
  offer: {
    offerId: number;
    offerName: string;
    skuCode: string;
    marketplaceType: string;
    connectionName: string;
  };
  expectedSource: string;
  actualSource: string;
  acknowledgedAt: string | null;
  acknowledgedBy: string | null;
  resolvedBy: string | null;
  resolutionNote: string | null;
  relatedAction: MismatchRelatedAction | null;
  thresholds: { warningPct: number; criticalPct: number };
  timeline: MismatchTimelineEvent[];
}

export interface MismatchRelatedAction {
  actionId: number;
  status: string;
  targetPrice: number;
  executedAt: string;
  reconciliationSource: string;
}

export interface MismatchTimelineEvent {
  eventType: string;
  timestamp: string;
  description: string;
  actor: string;
}

export interface MismatchSummary {
  totalActive: number;
  totalActiveDelta7d: number;
  criticalCount: number;
  criticalDelta7d: number;
  avgHoursUnresolved: number;
  avgHoursUnresolvedDelta7d: number;
  autoResolvedToday: number;
  autoResolvedYesterday: number;
  distributionByType: { type: MismatchType; count: number }[];
  timeline: { date: string; newCount: number; resolvedCount: number }[];
}

export interface MismatchFilter {
  type?: MismatchType[];
  connectionId?: number[];
  status?: MismatchStatus[];
  severity?: MismatchSeverity[];
  from?: string;
  to?: string;
  query?: string;
  offerId?: number;
}
