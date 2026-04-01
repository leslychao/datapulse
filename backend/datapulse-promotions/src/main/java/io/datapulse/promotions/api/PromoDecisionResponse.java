package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.ParticipationMode;
import io.datapulse.promotions.domain.PromoDecisionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PromoDecisionResponse(
        Long id,
        Long canonicalPromoProductId,
        Long promoEvaluationId,
        Integer policyVersion,
        PromoDecisionType decisionType,
        ParticipationMode participationMode,
        String executionMode,
        BigDecimal targetPromoPrice,
        String explanationSummary,
        Long decidedBy,
        OffsetDateTime createdAt
) {
}
