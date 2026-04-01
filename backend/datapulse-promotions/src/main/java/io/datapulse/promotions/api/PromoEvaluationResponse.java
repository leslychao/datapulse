package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.PromoEvaluationResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PromoEvaluationResponse(
        Long id,
        Long promoEvaluationRunId,
        Long canonicalPromoProductId,
        Long promoPolicyId,
        OffsetDateTime evaluatedAt,
        String currentParticipationStatus,
        BigDecimal promoPrice,
        BigDecimal regularPrice,
        BigDecimal discountPct,
        BigDecimal cogs,
        BigDecimal marginAtPromoPrice,
        BigDecimal marginAtRegularPrice,
        BigDecimal marginDeltaPct,
        BigDecimal effectiveCostRate,
        Integer stockAvailable,
        Integer expectedPromoDurationDays,
        BigDecimal avgDailyVelocity,
        BigDecimal stockDaysOfCover,
        Boolean stockSufficient,
        PromoEvaluationResult evaluationResult,
        String skipReason,
        OffsetDateTime createdAt
) {
}
