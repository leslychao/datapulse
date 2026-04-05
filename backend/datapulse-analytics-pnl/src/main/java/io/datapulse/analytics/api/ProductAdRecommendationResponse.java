package io.datapulse.analytics.api;

import java.math.BigDecimal;

import io.datapulse.analytics.domain.AdRecommendation;

public record ProductAdRecommendationResponse(
    long offerId,
    AdRecommendation recommendation,
    BigDecimal marginPct,
    BigDecimal currentDrrPct,
    BigDecimal estimatedDrrPct,
    BigDecimal maxCpc,
    String reasoning
) {}
