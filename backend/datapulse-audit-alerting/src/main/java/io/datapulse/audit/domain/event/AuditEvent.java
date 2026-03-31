package io.datapulse.audit.domain.event;

/**
 * Domain event published by source modules when a significant action occurs.
 * Consumed by {@code AuditEventListener} which persists it to {@code audit_log}.
 *
 * @param actorType     USER, SYSTEM, or SCHEDULER
 * @param actorUserId   nullable — NULL for SYSTEM / SCHEDULER actors
 * @param actionType    dot-separated key, e.g. "connection.create", "policy.update"
 * @param entityType    target entity table name, e.g. "marketplace_connection"
 * @param entityId      PK or composite key of the target entity
 * @param outcome       SUCCESS, DENIED, or FAILED
 * @param details       JSON string with context-specific payload (nullable)
 * @param ipAddress     client IP address (nullable)
 * @param correlationId request correlation UUID (nullable)
 */
public record AuditEvent(
        long workspaceId,
        String actorType,
        Long actorUserId,
        String actionType,
        String entityType,
        String entityId,
        String outcome,
        String details,
        String ipAddress,
        String correlationId
) {
}
