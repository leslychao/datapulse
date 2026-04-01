package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.ExecutionMode;
import io.datapulse.pricing.domain.PolicyStatus;
import io.datapulse.pricing.domain.PolicyType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PricePolicyResponse(
        Long id,
        String name,
        PolicyStatus status,
        PolicyType strategyType,
        String strategyParams,
        BigDecimal minMarginPct,
        BigDecimal maxPriceChangePct,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String guardConfig,
        ExecutionMode executionMode,
        Integer approvalTimeoutHours,
        Integer priority,
        Integer version,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
