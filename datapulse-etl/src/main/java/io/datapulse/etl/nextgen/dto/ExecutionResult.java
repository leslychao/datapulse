package io.datapulse.etl.nextgen.dto;

import java.util.UUID;

public record ExecutionResult(
    UUID executionId,
    String eventId,
    ExecutionStatus status,
    long rawRowsCount,
    String errorCode,
    String errorMessage
) {
}
