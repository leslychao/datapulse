package io.datapulse.etl.api;

import java.time.OffsetDateTime;

public record JobFilter(
        String status,
        OffsetDateTime from,
        OffsetDateTime to
) {
}
