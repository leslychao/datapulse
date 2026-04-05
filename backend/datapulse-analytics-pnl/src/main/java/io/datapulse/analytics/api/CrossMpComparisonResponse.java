package io.datapulse.analytics.api;

import java.math.BigDecimal;

public record CrossMpComparisonResponse(
    long sellerSkuId,
    String sourcePlatform,
    BigDecimal spend,
    BigDecimal drrPct,
    BigDecimal roas,
    BigDecimal cpo,
    BigDecimal cpc,
    BigDecimal crPct
) {}
