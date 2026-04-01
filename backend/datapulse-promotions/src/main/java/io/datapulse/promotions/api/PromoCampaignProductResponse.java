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
        String offerName,
        String marketplaceSku
) {
}
