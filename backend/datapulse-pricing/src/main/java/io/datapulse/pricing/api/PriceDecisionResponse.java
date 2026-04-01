package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.DecisionType;
import io.datapulse.pricing.domain.PolicyType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PriceDecisionResponse(
        Long id,
        Long pricingRunId,
        Long marketplaceOfferId,
        Long pricePolicyId,
        Integer policyVersion,
        DecisionType decisionType,
        BigDecimal currentPrice,
        BigDecimal targetPrice,
        BigDecimal priceChangeAmount,
        BigDecimal priceChangePct,
        PolicyType strategyType,
        BigDecimal strategyRawPrice,
        Object signalSnapshot,
        Object constraintsApplied,
        Object guardsEvaluated,
        String skipReason,
        String explanationSummary,
        String executionMode,
        OffsetDateTime createdAt
) {
}
