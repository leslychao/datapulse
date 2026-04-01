package io.datapulse.sellerops.api;

import java.time.OffsetDateTime;

public record QueueItemResponse(
        long itemId,
        String entityType,
        long entityId,
        String status,
        Long assignedTo,
        String note,
        OffsetDateTime createdAt
) {
}
