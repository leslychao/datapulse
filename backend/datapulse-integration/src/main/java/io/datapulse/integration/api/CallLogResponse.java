package io.datapulse.integration.api;

import java.time.OffsetDateTime;

public record CallLogResponse(
        long id,
        String endpoint,
        String httpMethod,
        Integer httpStatus,
        int durationMs,
        Integer requestSizeBytes,
        Integer responseSizeBytes,
        String correlationId,
        String errorDetails,
        int retryAttempt,
        OffsetDateTime createdAt
) {}
