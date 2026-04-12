package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.PromoRunStatus;
import io.datapulse.promotions.domain.PromoRunTriggerType;

import java.time.OffsetDateTime;

public record PromoEvaluationRunResponse(
        Long id,
        String sourcePlatform,
        PromoRunTriggerType triggerType,
        PromoRunStatus status,
        Integer totalProducts,
        Integer eligibleCount,
        Integer participateCount,
        Integer declineCount,
        Integer pendingReviewCount,
        Integer deactivateCount,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt
) {

    public PromoEvaluationRunResponse withSourcePlatform(String platform) {
        return new PromoEvaluationRunResponse(
            id, platform, triggerType, status,
            totalProducts, eligibleCount, participateCount,
            declineCount, pendingReviewCount, deactivateCount,
            startedAt, completedAt, createdAt);
    }
}
