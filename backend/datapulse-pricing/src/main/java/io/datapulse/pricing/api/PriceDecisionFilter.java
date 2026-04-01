package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.DecisionType;

import java.time.OffsetDateTime;

public record PriceDecisionFilter(
        Long connectionId,
        Long marketplaceOfferId,
        DecisionType decisionType,
        Long pricingRunId,
        OffsetDateTime from,
        OffsetDateTime to
) {
}
