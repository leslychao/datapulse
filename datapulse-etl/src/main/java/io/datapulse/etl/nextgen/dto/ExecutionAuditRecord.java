package io.datapulse.etl.nextgen.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExecutionAuditRecord(
    UUID executionId,
    String eventId,
    String marketplace,
    String eventSourceName,
    ExecutionStatus status,
    long rawRowsCount,
    int retryCount,
    String errorCode,
    String errorMessage,
    OffsetDateTime timestamp
) {
}
