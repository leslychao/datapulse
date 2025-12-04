package io.datapulse.etl.nextgen.dto;

import java.util.UUID;

public record RetrySignal(
    UUID executionId,
    String eventId,
    String sourceName,
    String marketplace,
    int retryAfterSeconds,
    int retryCount
) {
}
