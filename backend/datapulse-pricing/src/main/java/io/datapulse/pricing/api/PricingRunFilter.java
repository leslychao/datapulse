package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.RunStatus;

import java.time.OffsetDateTime;

public record PricingRunFilter(
        Long connectionId,
        RunStatus status,
        OffsetDateTime from,
        OffsetDateTime to
) {
}
