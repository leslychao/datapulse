package io.datapulse.etl.domain.normalized;

import java.math.BigDecimal;

public record NormalizedPromoProduct(
        String externalPromoId,
        String marketplaceSku,
        String participationStatus,
        BigDecimal requiredPrice,
        BigDecimal currentPrice,
        BigDecimal maxPromoPrice,
        BigDecimal maxDiscountPct,
        String addMode,
        Integer minStockRequired,
        Integer stockAvailable
) {}
