package io.datapulse.audit.domain.event;

/**
 * Published after an alert_event row is inserted (both event-driven and rule-based).
 * Consumed by: notification fan-out, WebSocket alert push.
 */
public record AlertEventCreatedEvent(
        long alertEventId,
        long workspaceId,
        Long connectionId,
        String ruleType,
        String severity,
        String title,
        String status,
        boolean blocksAutomation
) {
}
