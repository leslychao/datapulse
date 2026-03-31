package io.datapulse.integration.api;

import java.time.OffsetDateTime;

public record ConnectionSummaryResponse(
        Long id,
        String marketplaceType,
        String name,
        String status,
        OffsetDateTime lastCheckAt,
        OffsetDateTime lastSuccessAt,
        String lastErrorCode
) {
}
