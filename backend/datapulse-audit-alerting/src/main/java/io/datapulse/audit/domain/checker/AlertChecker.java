package io.datapulse.audit.domain.checker;

import io.datapulse.audit.api.AlertRuleResponse;

/**
 * Strategy interface for scheduled alert checkers.
 * Each implementation handles one {@code AlertRuleType} and evaluates
 * conditions per workspace + connection.
 */
public interface AlertChecker {

    /**
     * @return the rule type this checker handles (e.g. "STALE_DATA", "MISSING_SYNC")
     */
    String ruleType();

    /**
     * Evaluates the rule for a specific workspace. The checker is responsible for:
     * <ol>
     *   <li>Querying source data to detect violations</li>
     *   <li>Creating alert_events for new violations</li>
     *   <li>Auto-resolving existing alerts when conditions clear</li>
     * </ol>
     */
    void check(AlertRuleResponse rule);
}
