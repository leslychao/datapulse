package io.datapulse.etl.nextgen.dto;

import java.util.UUID;

public record RawIngestPayload(
    UUID executionId,
    String eventId,
    String sourceName,
    long rawRowsCount,
    byte[] rawContent
) {
}
