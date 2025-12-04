package io.datapulse.etl.nextgen.dto;

import java.util.UUID;

public record ExecutionCommand(
    UUID executionId,
    String eventId,
    String sourceName,
    String marketplace,
    int orderIndex,
    int retryCount
) {
}
