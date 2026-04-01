package io.datapulse.analytics.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PostingDetailResponse(
        long entryId,
        String entryType,
        String attributionLevel,
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
        BigDecimal netPayout
) {}
