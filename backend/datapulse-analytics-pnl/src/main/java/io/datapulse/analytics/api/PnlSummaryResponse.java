package io.datapulse.analytics.api;

import java.math.BigDecimal;

public record PnlSummaryResponse(
        long connectionId,
        String sourcePlatform,
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
        BigDecimal advertisingCost,
        BigDecimal marketplacePnl,
        BigDecimal fullPnl
) {}
