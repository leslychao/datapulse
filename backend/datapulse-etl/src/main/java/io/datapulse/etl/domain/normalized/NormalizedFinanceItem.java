package io.datapulse.etl.domain.normalized;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record NormalizedFinanceItem(
        String externalEntryId,
        String entryType,
        String postingId,
        String orderId,
        String sellerSku,
        String warehouseId,
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
        String currency,
        OffsetDateTime entryDate,
        String attributionLevel
) {}
