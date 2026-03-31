package io.datapulse.etl.domain.normalized;

import java.math.BigDecimal;

public record NormalizedPriceItem(
        String marketplaceSku,
        BigDecimal price,
        BigDecimal discountPrice,
        BigDecimal discountPct,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String currency
) {}
