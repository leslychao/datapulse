package io.datapulse.analytics.api;

import java.math.BigDecimal;

public record ReturnReasonResponse(
    String reason,
    int returnCount,
    int returnQuantity,
    BigDecimal returnAmount,
    BigDecimal percent,
    int productCount
) {}
