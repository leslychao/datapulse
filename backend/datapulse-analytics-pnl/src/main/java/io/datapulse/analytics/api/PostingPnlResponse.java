package io.datapulse.analytics.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PostingPnlResponse(
        String postingId,
        String sourcePlatform,
        String orderId,
        Long sellerSkuId,
        Long productId,
        String skuCode,
        String productName,
        LocalDate financeDate,
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
        Integer quantity,
        BigDecimal grossCogs,
        BigDecimal netCogs,
        String cogsStatus,
        BigDecimal reconciliationResidual
) {}
