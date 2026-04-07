package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.RunStatus;
import io.datapulse.pricing.domain.RunTriggerType;

import java.time.OffsetDateTime;

public record PricingRunResponse(
        Long id,
        Long connectionId,
        String connectionName,
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
        OffsetDateTime createdAt,
        int simulatedDecisionCount
) {

    public PricingRunResponse withEnrichment(String name, int simCount) {
        return new PricingRunResponse(
            id, connectionId, name, triggerType, status,
            totalOffers, eligibleCount, changeCount,
            skipCount, holdCount, startedAt, completedAt,
            errorDetails, createdAt, simCount);
    }
}
