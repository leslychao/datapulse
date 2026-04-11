package io.datapulse.analytics.api;

import java.math.BigDecimal;

public record ProductReturnResponse(
    String sourcePlatform,
    long productId,
    long sellerSkuId,
    String skuCode,
    String productName,
    int period,
    int returnCount,
    int returnQuantity,
    BigDecimal returnAmount,
    int saleCount,
    int saleQuantity,
    BigDecimal returnRatePct,
    String topReturnReason,
    int distinctReasonCount
) {}
