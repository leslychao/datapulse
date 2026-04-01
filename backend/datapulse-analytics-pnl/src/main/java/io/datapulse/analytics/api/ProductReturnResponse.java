package io.datapulse.analytics.api;

import java.math.BigDecimal;

public record ProductReturnResponse(
        long connectionId,
        String sourcePlatform,
        long productId,
        long sellerSkuId,
        String skuCode,
        String productName,
        int period,
        int returnCount,
        int returnQuantity,
        BigDecimal returnAmount,
        int saleQuantity,
        BigDecimal returnRatePct,
        BigDecimal financialRefundAmount,
        BigDecimal penaltiesAmount,
        String topReturnReason
) {}
