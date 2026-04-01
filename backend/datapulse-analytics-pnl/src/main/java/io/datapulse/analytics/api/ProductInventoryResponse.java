package io.datapulse.analytics.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductInventoryResponse(
        long connectionId,
        String sourcePlatform,
        long productId,
        long sellerSkuId,
        String skuCode,
        String productName,
        Integer warehouseId,
        String warehouseName,
        LocalDate analysisDate,
        int available,
        Integer reserved,
        BigDecimal avgDailySales14d,
        BigDecimal daysOfCover,
        String stockOutRisk,
        BigDecimal costPrice,
        BigDecimal frozenCapital,
        Integer recommendedReplenishment
) {}
