package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.RunStatus;
import io.datapulse.pricing.domain.RunTriggerType;

import java.time.OffsetDateTime;

public record PricingRunResponse(
        Long id,
        Long connectionId,
        RunTriggerType triggerType,
        RunStatus status,
        Integer totalOffers,
        Integer eligibleCount,
        Integer changeCount,
        Integer skipCount,
        Integer holdCount,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Object errorDetails,
        OffsetDateTime createdAt
) {
}
