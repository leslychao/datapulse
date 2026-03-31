package io.datapulse.audit.api;

import java.time.OffsetDateTime;

public record AuditLogResponse(
        long id,
        long workspaceId,
        String actorType,
        Long actorUserId,
        String actionType,
        String entityType,
        String entityId,
        String outcome,
        String details,
        String ipAddress,
        String correlationId,
        OffsetDateTime createdAt
) {
}
