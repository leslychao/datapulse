package io.datapulse.audit.domain.checker;

import io.datapulse.audit.api.AlertRuleResponse;
import io.datapulse.audit.domain.AlertRuleType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Checks for reconciliation residual anomalies in ClickHouse mart_posting_pnl.
 * Triggered after each ClickHouse materialization (ETL_SYNC_COMPLETED event).
 *
 * <p>Currently a stub — full implementation requires ClickHouse DataSource,
 * which becomes available in the Analytics & P&L module (Phase C/D).
 */
@Slf4j
@Component
public class ResidualAnomalyChecker implements AlertChecker {

    @Override
    public String ruleType() {
        return AlertRuleType.RESIDUAL_ANOMALY.name();
    }

    @Override
    public void check(AlertRuleResponse rule) {
        log.debug("ResidualAnomalyChecker skipped: ClickHouse analytics not available yet. "
                + "ruleId={}, workspaceId={}", rule.id(), rule.workspaceId());
    }
}
