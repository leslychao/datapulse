package io.datapulse.analytics.persistence;

import java.math.BigDecimal;

public record CategoryAdAvg(
    String category,
    BigDecimal avgCpc,
    BigDecimal avgCr
) {}
