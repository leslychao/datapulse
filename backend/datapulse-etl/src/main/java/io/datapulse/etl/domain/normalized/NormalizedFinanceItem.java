package io.datapulse.etl.domain.normalized;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.datapulse.etl.domain.FinanceEntryType;

/**
 * Marketplace-agnostic normalized finance record.
 * Composite row model (DD-8): one provider entry maps to one record with 12 measure columns.
 *
 * <p>Sign convention (canonical): positive = credit to seller, negative = debit from seller.
 * WB debit fields are negated during normalization (DD-7).
 * Ozon signs are preserved as-is (DD-4).</p>
 *
 * @param sellerSku      raw vendor code (WB: sa_name) or offer_id — for product_master fallback lookup
 * @param marketplaceSku marketplace-specific SKU (WB: nm_id, Ozon: items[].sku) — for marketplace_offer lookup
 * @param warehouseExternalId external warehouse identifier (WB: ppvz_office_id, Ozon: posting.warehouse_id)
 */
public record NormalizedFinanceItem(
        String externalEntryId,
        FinanceEntryType entryType,
        String postingId,
        String orderId,
        String sellerSku,
        String marketplaceSku,
        String warehouseExternalId,
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
        OffsetDateTime entryDate
) {}
