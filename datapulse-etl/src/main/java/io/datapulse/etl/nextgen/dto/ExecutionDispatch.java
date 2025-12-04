package io.datapulse.etl.nextgen.dto;

import java.util.UUID;

public record ExecutionDispatch(
    UUID executionId,
    String eventId,
    String sourceName,
    String marketplace,
    int orderIndex,
    int retryCount
) {
}
