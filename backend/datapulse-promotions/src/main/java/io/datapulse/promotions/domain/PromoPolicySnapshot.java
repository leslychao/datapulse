package io.datapulse.promotions.domain;

import io.datapulse.promotions.persistence.PromoPolicyEntity;

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

    public static PromoPolicySnapshot from(PromoPolicyEntity policy) {
        return new PromoPolicySnapshot(
                policy.getId(), policy.getVersion(), policy.getName(),
                policy.getParticipationMode(), policy.getMinMarginPct(),
                policy.getMinStockDaysOfCover(), policy.getMaxPromoDiscountPct(),
                policy.getAutoParticipateCategories(), policy.getAutoDeclineCategories(),
                policy.getEvaluationConfig());
    }

    public static PromoPolicySnapshot empty() {
        return new PromoPolicySnapshot(null, 0, null, ParticipationMode.RECOMMENDATION,
                BigDecimal.ZERO, 7, null, null, null, null);
    }
}
