package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.DecisionType;

import java.time.OffsetDateTime;
import java.util.List;

public record PriceDecisionFilter(
        String sourcePlatform,
        Long marketplaceOfferId,
        List<DecisionType> decisionType,
        Long pricingRunId,
        String executionMode,
        OffsetDateTime from,
        OffsetDateTime to
) {
}
