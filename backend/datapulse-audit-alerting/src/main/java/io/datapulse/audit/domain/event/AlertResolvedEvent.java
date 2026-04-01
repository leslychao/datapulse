package io.datapulse.audit.domain.event;

/**
 * Published when an alert_event transitions to RESOLVED or AUTO_RESOLVED.
 * Consumed by: notification fan-out, WebSocket alert push.
 */
public record AlertResolvedEvent(
        long alertEventId,
        long workspaceId,
        Long connectionId,
        String severity,
        String title,
        String resolvedReason
) {
}
