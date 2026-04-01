package io.datapulse.pricing.domain;

import java.math.BigDecimal;

public record PolicySnapshot(
        long policyId,
        int version,
        String name,
        PolicyType strategyType,
        String strategyParams,
        BigDecimal minMarginPct,
        BigDecimal maxPriceChangePct,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String guardConfig,
        ExecutionMode executionMode
) {
}
