package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.ExecutionMode;
import io.datapulse.pricing.domain.PolicyStatus;
import io.datapulse.pricing.domain.PolicyType;

import java.time.OffsetDateTime;

public record PricePolicySummaryResponse(
        Long id,
        String name,
        PolicyStatus status,
        PolicyType strategyType,
        ExecutionMode executionMode,
        Integer priority,
        Integer version,
        Integer assignmentsCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
