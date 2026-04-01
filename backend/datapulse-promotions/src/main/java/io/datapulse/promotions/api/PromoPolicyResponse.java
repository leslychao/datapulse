package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.ParticipationMode;
import io.datapulse.promotions.domain.PromoPolicyStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PromoPolicyResponse(
        Long id,
        String name,
        PromoPolicyStatus status,
        ParticipationMode participationMode,
        BigDecimal minMarginPct,
        Integer minStockDaysOfCover,
        BigDecimal maxPromoDiscountPct,
        String autoParticipateCategories,
        String autoDeclineCategories,
        String evaluationConfig,
        Integer version,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
