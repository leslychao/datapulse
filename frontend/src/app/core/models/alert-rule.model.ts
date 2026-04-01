import type { AlertRuleType, AlertSeverity } from './alert.model';

export type { AlertRuleType, AlertSeverity };

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
