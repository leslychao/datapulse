package io.datapulse.analytics.api;

import java.math.BigDecimal;

public record ProductPnlResponse(
        long connectionId,
        String sourcePlatform,
        long sellerSkuId,
        long productId,
        int period,
        String attributionLevel,
        String skuCode,
        String productName,
        BigDecimal revenueAmount,
        BigDecimal marketplaceCommissionAmount,
        BigDecimal acquiringCommissionAmount,
        BigDecimal logisticsCostAmount,
        BigDecimal storageCostAmount,
        BigDecimal penaltiesAmount,
        BigDecimal marketingCostAmount,
        BigDecimal acceptanceCostAmount,
        BigDecimal otherMarketplaceChargesAmount,
        BigDecimal compensationAmount,
        BigDecimal refundAmount,
        BigDecimal netPayout,
        BigDecimal grossCogs,
        BigDecimal netCogs,
        String cogsStatus,
        BigDecimal advertisingCost,
        BigDecimal marketplacePnl,
        BigDecimal fullPnl
) {}
