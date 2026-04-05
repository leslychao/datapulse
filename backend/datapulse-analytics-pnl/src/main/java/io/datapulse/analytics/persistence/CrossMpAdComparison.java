package io.datapulse.analytics.persistence;

import java.math.BigDecimal;

public record CrossMpAdComparison(
    long sellerSkuId,
    String sourcePlatform,
    BigDecimal spend,
    BigDecimal drrPct,
    BigDecimal roas,
    BigDecimal cpo,
    BigDecimal cpc,
    BigDecimal crPct
) {}
