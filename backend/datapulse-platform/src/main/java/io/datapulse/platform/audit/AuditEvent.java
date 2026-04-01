package io.datapulse.platform.audit;

/**
 * Domain event published by source modules when a significant action occurs.
 * Consumed by AuditEventListener (in datapulse-audit-alerting) which persists to audit_log.
 *
 * @param workspaceId   nullable — NULL for cross-workspace events (tenant.create, user.provision)
 * @param actorType     USER, SYSTEM, or SCHEDULER
 * @param actorUserId   nullable — NULL for SYSTEM / SCHEDULER actors
 * @param actionType    dot-separated key, e.g. "workspace.create", "member.invite"
 * @param entityType    target entity table name, e.g. "workspace", "workspace_invitation"
 * @param entityId      PK or composite key of the target entity
 * @param outcome       SUCCESS, DENIED, or FAILED
 * @param details       JSON string with context-specific payload (nullable)
 * @param ipAddress     client IP address (nullable)
 * @param correlationId request correlation UUID (nullable)
 */
public record AuditEvent(
        Long workspaceId,
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
