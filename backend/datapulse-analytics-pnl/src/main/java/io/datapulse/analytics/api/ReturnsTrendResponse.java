package io.datapulse.analytics.api;

import java.math.BigDecimal;

public record ReturnsTrendResponse(
    int period,
    int returnQuantity,
    int saleQuantity,
    BigDecimal returnRatePct
) {}
