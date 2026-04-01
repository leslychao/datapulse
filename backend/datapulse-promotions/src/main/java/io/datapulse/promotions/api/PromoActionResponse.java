package io.datapulse.promotions.api;

import io.datapulse.promotions.domain.PromoActionStatus;
import io.datapulse.promotions.domain.PromoActionType;
import io.datapulse.promotions.domain.PromoExecutionMode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PromoActionResponse(
        Long id,
        Long promoDecisionId,
        Long canonicalPromoCampaignId,
        Long marketplaceOfferId,
        PromoActionType actionType,
        BigDecimal targetPromoPrice,
        PromoActionStatus status,
        int attemptCount,
        String lastError,
        PromoExecutionMode executionMode,
        OffsetDateTime freezeAtSnapshot,
        String cancelReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
