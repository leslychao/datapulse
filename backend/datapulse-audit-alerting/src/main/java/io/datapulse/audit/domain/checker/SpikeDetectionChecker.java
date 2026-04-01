package io.datapulse.audit.domain.checker;

import io.datapulse.audit.api.AlertRuleResponse;
import io.datapulse.audit.domain.AlertRuleType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Checks for period-over-period spikes in key financial measures.
 * Triggered after each ClickHouse materialization (ETL_SYNC_COMPLETED event).
 *
 * <p>Currently a stub — full implementation requires ClickHouse DataSource
 * and star schema tables, which become available in Phase C/D.
 */
@Slf4j
@Component
public class SpikeDetectionChecker implements AlertChecker {

    @Override
    public String ruleType() {
        return AlertRuleType.SPIKE_DETECTION.name();
    }

    @Override
    public void check(AlertRuleResponse rule) {
        log.debug("SpikeDetectionChecker skipped: ClickHouse analytics not available yet. "
                + "ruleId={}, workspaceId={}", rule.id(), rule.workspaceId());
    }
}
