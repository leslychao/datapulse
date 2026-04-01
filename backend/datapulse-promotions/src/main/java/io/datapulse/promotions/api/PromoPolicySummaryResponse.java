package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.ParticipationMode;
import io.datapulse.promotions.domain.PromoPolicyStatus;

import java.time.OffsetDateTime;

public record PromoPolicySummaryResponse(
        Long id,
        String name,
        PromoPolicyStatus status,
        ParticipationMode participationMode,
        Integer version,
        OffsetDateTime updatedAt
) {
}
