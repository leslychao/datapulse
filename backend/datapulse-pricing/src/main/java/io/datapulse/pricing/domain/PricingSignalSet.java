package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Assembled signals for a single marketplace offer at pricing decision time.
 * All fields are nullable — absence of a signal is meaningful
 * (e.g. missing COGS → decision HOLD for TARGET_MARGIN strategy).
 */
public record PricingSignalSet(
        BigDecimal currentPrice,
        BigDecimal cogs,
        String productStatus,
        Integer availableStock,
        boolean manualLockActive,
        boolean promoActive,
        BigDecimal avgCommissionPct,
        BigDecimal avgLogisticsPerUnit,
        BigDecimal returnRatePct,
        BigDecimal adCostRatio,
        OffsetDateTime lastPriceChangeAt,
        Integer priceReversalsInPeriod,
        OffsetDateTime dataFreshnessAt
) {
}
