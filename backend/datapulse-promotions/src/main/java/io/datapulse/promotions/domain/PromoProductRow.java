package io.datapulse.promotions.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PromoProductRow(
        long promoProductId,
        long campaignId,
        long marketplaceOfferId,
        Long categoryId,
        String participationStatus,
        BigDecimal requiredPrice,
        BigDecimal currentPrice,
        BigDecimal cogs,
        BigDecimal effectiveCostRate,
        Integer stockAvailable,
        BigDecimal avgDailyVelocity,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo,
        OffsetDateTime freezeAt
) {
}
