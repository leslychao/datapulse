export type AlertRuleType =
  | 'STALE_DATA'
  | 'MISSING_SYNC'
  | 'RESIDUAL_ANOMALY'
  | 'SPIKE_DETECTION'
  | 'MISMATCH';

export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

export interface AlertRule {
  id: number;
  ruleType: AlertRuleType;
  targetEntityType: string;
  config: Record<string, number>;
  enabled: boolean;
  severity: AlertSeverity;
  blocksAutomation: boolean;
  lastTriggeredAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateAlertRuleRequest {
  config: Record<string, number>;
  enabled: boolean;
  severity: AlertSeverity;
  blocksAutomation: boolean;
}
