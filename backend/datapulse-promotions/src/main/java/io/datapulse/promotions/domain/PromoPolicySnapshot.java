package io.datapulse.promotions.domain;

import java.math.BigDecimal;

public record PromoPolicySnapshot(
        Long policyId,
        int version,
        String name,
        ParticipationMode participationMode,
        BigDecimal minMarginPct,
        int minStockDaysOfCover,
        BigDecimal maxPromoDiscountPct,
        String autoParticipateCategories,
        String autoDeclineCategories,
        String evaluationConfig
) {
}
