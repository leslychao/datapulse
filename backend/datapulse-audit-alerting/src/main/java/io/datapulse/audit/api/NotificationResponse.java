package io.datapulse.audit.api;

import java.time.OffsetDateTime;

public record NotificationResponse(
        long id,
        long workspaceId,
        long userId,
        Long alertEventId,
        String notificationType,
        String title,
        String body,
        String severity,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {
}
