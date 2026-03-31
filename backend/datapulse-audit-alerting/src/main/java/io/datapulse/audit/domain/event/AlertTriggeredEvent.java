package io.datapulse.audit.domain.event;

/**
 * Domain event published by modules for event-driven alerts
 * (action failed, stuck state, reconciliation mismatch, etc.).
 * Consumed by {@code AlertEventListener} which persists it to {@code alert_event}
 * with {@code alert_rule_id = NULL}.
 *
 * @param connectionId     nullable — NULL for workspace-wide alerts
 * @param severity         INFO, WARNING, or CRITICAL
 * @param details          JSON string with rule-type-specific evidence (nullable)
 * @param blocksAutomation whether this alert blocks the pricing pipeline for the connection
 */
public record AlertTriggeredEvent(
        long workspaceId,
        Long connectionId,
        String severity,
        String title,
        String details,
        boolean blocksAutomation
) {
}
