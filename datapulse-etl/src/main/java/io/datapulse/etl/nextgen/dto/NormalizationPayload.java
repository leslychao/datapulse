package io.datapulse.etl.nextgen.dto;

import java.util.UUID;

public record NormalizationPayload(
    UUID executionId,
    String eventId,
    String sourceName,
    long rawRowsCount
) {
}
