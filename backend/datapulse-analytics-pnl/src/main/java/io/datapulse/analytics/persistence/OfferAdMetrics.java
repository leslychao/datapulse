package io.datapulse.analytics.persistence;

import java.math.BigDecimal;

public record OfferAdMetrics(
    long offerId,
    BigDecimal spend30d,
    long clicks30d,
    long orders30d,
    BigDecimal revenue30d,
    int adDays,
    BigDecimal avgCpc,
    BigDecimal avgCr,
    BigDecimal currentDrrPct
) {}
