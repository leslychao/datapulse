package io.datapulse.pricing.api;

import java.time.OffsetDateTime;

public record AdvisorResponse(
    String advice,
    String error,
    OffsetDateTime generatedAt,
    OffsetDateTime cachedUntil
) {
}
