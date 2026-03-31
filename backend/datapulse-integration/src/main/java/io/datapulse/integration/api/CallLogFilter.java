package io.datapulse.integration.api;

import java.time.OffsetDateTime;

public record CallLogFilter(
        OffsetDateTime from,
        OffsetDateTime to,
        String endpoint,
        Integer httpStatus
) {}
