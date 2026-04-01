package io.datapulse.analytics.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProvenanceEntryResponse(
        long id,
        long connectionId,
        String sourcePlatform,
        String externalEntryId,
        String entryType,
        String postingId,
        String orderId,
        Long sellerSkuId,
        BigDecimal revenueAmount,
        BigDecimal marketplaceCommissionAmount,
        BigDecimal acquiringCommissionAmount,
        BigDecimal logisticsCostAmount,
        BigDecimal storageCostAmount,
        BigDecimal penaltiesAmount,
        BigDecimal acceptanceCostAmount,
        BigDecimal marketingCostAmount,
        BigDecimal otherMarketplaceChargesAmount,
        BigDecimal compensationAmount,
        BigDecimal refundAmount,
        BigDecimal netPayout,
        OffsetDateTime entryDate,
        Long jobExecutionId
) {}
