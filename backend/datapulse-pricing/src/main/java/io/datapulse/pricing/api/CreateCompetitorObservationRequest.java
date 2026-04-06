package io.datapulse.pricing.api;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateCompetitorObservationRequest(
        @NotNull BigDecimal competitorPrice,
        OffsetDateTime observedAt
) {
}
