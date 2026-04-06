package io.datapulse.pricing.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CompetitorObservationResponse(
        long id,
        long competitorMatchId,
        BigDecimal competitorPrice,
        String currency,
        OffsetDateTime observedAt,
        OffsetDateTime createdAt
) {
}
