package io.datapulse.audit.api;

import java.time.LocalDate;

public record AuditLogFilter(
        String entityType,
        String entityId,
        Long actorUserId,
        String actionType,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
