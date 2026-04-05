package io.datapulse.analytics.persistence;

import java.math.BigDecimal;

public record RecommendationOfferRow(
    long offerId,
    long connectionId,
    String marketplaceSku,
    BigDecimal currentPrice,
    BigDecimal costPrice,
    BigDecimal marginPct,
    String category
) {}
