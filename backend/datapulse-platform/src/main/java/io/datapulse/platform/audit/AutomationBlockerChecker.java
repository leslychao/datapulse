package io.datapulse.platform.audit;

/**
 * Cross-module contract: checks whether automation (pricing, execution)
 * is blocked for a given workspace + connection due to active alerts
 * with {@code blocks_automation = true}.
 *
 * <p>Implemented by the Audit & Alerting module.
 * Consumed by Pricing pipeline (run-level pre-check).
 */
public interface AutomationBlockerChecker {

    boolean isBlocked(long workspaceId, long connectionId);
}
