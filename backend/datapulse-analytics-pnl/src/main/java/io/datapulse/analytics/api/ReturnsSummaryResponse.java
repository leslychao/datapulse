package io.datapulse.analytics.api;

import java.math.BigDecimal;

public record ReturnsSummaryResponse(
        String sourcePlatform,
        int returnCount,
        int returnQuantity,
        BigDecimal returnAmount,
        int saleCount,
        int saleQuantity,
        BigDecimal returnRatePct,
        BigDecimal financialRefundAmount,
        BigDecimal penaltiesAmount,
        String topReturnReason
) {}
