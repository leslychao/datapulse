package io.datapulse.etl.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record JobExecutionResponse(
        long id,
        long connectionId,
        String eventType,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        JsonNode errorDetails,
        OffsetDateTime createdAt
) {
}
