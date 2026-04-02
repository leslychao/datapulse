package io.datapulse.promotions.api;

import java.math.BigDecimal;

public record PromoCampaignProductResponse(
        Long id,
        Long marketplaceOfferId,
        String participationStatus,
        BigDecimal requiredPrice,
        BigDecimal currentPrice,
        BigDecimal maxPromoPrice,
        BigDecimal maxDiscountPct,
        Integer stockAvailable,
        String addMode,
        String participationDecisionSource,
        String productName,
        String marketplaceSku,
        String sellerSkuCode,
        BigDecimal discountPct,
        BigDecimal marginAtPromoPrice,
        BigDecimal stockDaysOfCover,
        String evaluationResult,
        String decisionType,
        String actionStatus,
        Long actionId
) {
}
