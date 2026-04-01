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
        Object strategyParams,
        BigDecimal minMarginPct,
        BigDecimal maxPriceChangePct,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Object guardConfig,
        ExecutionMode executionMode,
        Integer approvalTimeoutHours,
        Integer priority,
        Integer version,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
