export type AlertSeverity = 'CRITICAL' | 'WARNING' | 'INFO';

export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'AUTO_RESOLVED';

export type AlertRuleType =
  | 'STALE_DATA'
  | 'MISSING_SYNC'
  | 'RESIDUAL_ANOMALY'
  | 'SPIKE_DETECTION'
  | 'MISMATCH'
  | 'ACTION_FAILED'
  | 'STUCK_STATE'
  | 'RECONCILIATION_FAILED'
  | 'POISON_PILL'
  | 'PROMO_MISMATCH'
  | 'ACTION_DEFERRED';

export interface AlertEvent {
  id: number;
  alertRuleId: number;
  ruleType: AlertRuleType;
  severity: AlertSeverity;
  status: AlertStatus;
  title: string;
  details: Record<string, unknown>;
  blocksAutomation: boolean;
  sourcePlatform: string | null;
  connectionName: string | null;
  openedAt: string;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
}

export interface AlertSummary {
  openCritical: number;
  openWarning: number;
  acknowledged: number;
  resolvedLast7Days: number;
}

export interface AlertFilter {
  ruleType?: AlertRuleType[];
  severity?: AlertSeverity[];
  sourcePlatform?: string;
  status?: AlertStatus[];
  blocksAutomation?: boolean;
}
