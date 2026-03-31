package io.datapulse.etl.domain;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Unified finance entry type enum mapping Ozon operation_type values and WB doc_type_name.
 * 44 Ozon types (23 empirical + 21 from official enum) + WB composite types.
 *
 * <p>{@link #canonicalName()} maps WB-specific types to canonical entry type names
 * stored in {@code canonical_finance_entry.entry_type}.</p>
 *
 * <p>{@link #primaryMeasure()} returns the canonical measure column for the entry type,
 * used by Ozon normalizer to route {@code operation.amount} into the correct column.</p>
 */
public enum FinanceEntryType {

    // --- Ozon empirical (23 types, confirmed from real API) ---
    SALE_ACCRUAL("OperationAgentDeliveredToCustomer", MeasureColumn.REVENUE),
    RETURN_REVERSAL("ClientReturnAgentOperation", MeasureColumn.REFUND),
    STORNO_CORRECTION("OperationAgentStornoDeliveredToCustomer", MeasureColumn.REFUND),
    RETURN_LOGISTICS("OperationItemReturn", MeasureColumn.LOGISTICS),
    ACQUIRING("MarketplaceRedistributionOfAcquiringOperation", MeasureColumn.ACQUIRING),
    BRAND_COMMISSION("MarketplaceServiceBrandCommission", MeasureColumn.MARKETPLACE_COMMISSION),
    LOGISTICS("MarketplaceServiceItemCrossdocking", MeasureColumn.LOGISTICS),
    PACKAGING("OperationElectronicServiceStencil", MeasureColumn.OTHER),
    STORAGE("OperationMarketplaceServiceStorage", MeasureColumn.STORAGE),
    SUBSCRIPTION("StarsMembership", MeasureColumn.OTHER),
    REVIEWS_PURCHASE("MarketplaceSaleReviewsOperation", MeasureColumn.MARKETING),
    DISPOSAL("DisposalReasonFailedToPickupOnTime", MeasureColumn.PENALTIES),
    DISPOSAL_DAMAGED("DisposalReasonDamagedPackaging", MeasureColumn.PENALTIES),
    COMPENSATION("AccrualInternalClaim", MeasureColumn.COMPENSATION),
    COMPENSATION_WITHOUT_DOCS("AccrualWithoutDocs", MeasureColumn.COMPENSATION),
    SELLER_COMPENSATION("MarketplaceSellerCompensationOperation", MeasureColumn.COMPENSATION),
    FBS_RETURN_LOGISTICS("OperationReturnGoodsFBSofRMS", MeasureColumn.LOGISTICS),
    CPC_ADVERTISING("OperationMarketplaceCostPerClick", MeasureColumn.MARKETING),
    PROMO_CPC("OperationPromotionWithCostPerOrder", MeasureColumn.MARKETING),
    DEFECT_PENALTY("DefectRateCancellation", MeasureColumn.PENALTIES),
    REVIEWS_POINTS("OperationPointsForReviews", MeasureColumn.MARKETING),
    SHIPMENT_DELAY_FINE("DefectFineShipmentDelayRated", MeasureColumn.PENALTIES),
    CANCELLATION_FINE("DefectFineCancellation", MeasureColumn.PENALTIES),

    // --- Ozon official enum (21 types, C-docs, not yet observed) ---
    DELIVERY_CANCEL_ACCRUAL("OperationAgentDeliveredToCustomerCanceled", MeasureColumn.REFUND),
    CLAIM("OperationClaim", MeasureColumn.COMPENSATION),
    CORRECTION("OperationCorrectionSeller", MeasureColumn.OTHER),
    DEFECTIVE_WRITEOFF("OperationDefectiveWriteOff", MeasureColumn.COMPENSATION),
    LACK_WRITEOFF("OperationLackWriteOff", MeasureColumn.COMPENSATION),
    SETOFF("OperationSetOff", MeasureColumn.OTHER),
    CROSSDOCK_LOGISTICS("OperationMarketplaceCrossDockServiceWriteOff", MeasureColumn.LOGISTICS),
    RFBS_RETURN("ReturnAgentOperationRFBS", MeasureColumn.LOGISTICS),
    REEXPOSURE_DELIVERY("MarketplaceSellerReexposureDeliveryReturnOperation", MeasureColumn.LOGISTICS),
    SHIPPING_COMPENSATION("MarketplaceSellerShippingCompensationReturnOperation", MeasureColumn.COMPENSATION),
    MARKETING_ACTION("MarketplaceMarketingActionCostOperation", MeasureColumn.MARKETING),
    PREMIUM_CASHBACK("OperationMarketplaceServicePremiumCashback", MeasureColumn.MARKETING),
    PREMIUM_PROMOTION("MarketplaceServicePremiumPromotion", MeasureColumn.MARKETING),
    PREMIUM_SELLER_BONUS("MarketplaceServicePremiumCashbackIndividualPoints", MeasureColumn.MARKETING),
    PREMIUM_SUBSCRIPTION("OperationSubscriptionPremium", MeasureColumn.OTHER),
    FBS_RETURN_STORAGE_PVZ("MarketplaceReturnStorageServiceAtThePickupPointFbsItem", MeasureColumn.STORAGE),
    FBS_RETURN_STORAGE_WH("MarketplaceReturnStorageServiceInTheWarehouseFbsItem", MeasureColumn.STORAGE),
    KGT_LOGISTICS("MarketplaceServiceItemDeliveryKGT", MeasureColumn.LOGISTICS),
    WITHHOLDING_UNDELIVERABLE("OperationMarketplaceWithHoldingForUndeliverableGoods", MeasureColumn.PENALTIES),
    SEARCH_PROMOTION("OperationElectronicServicesPromotionInSearch", MeasureColumn.MARKETING),
    BRAND_SHELF("OperationMarketplaceServiceItemElectronicServicesBrandShelf", MeasureColumn.MARKETING),

    // --- Alias from official enum (ItemAgentServiceStarsMembership = StarsMembership) ---
    SUBSCRIPTION_ALIAS("ItemAgentServiceStarsMembership", MeasureColumn.OTHER),

    // --- WB composite types (entry_type from doc_type_name) ---
    WB_SALE("Продажа", MeasureColumn.REVENUE),
    WB_RETURN("Возврат", MeasureColumn.REFUND),
    WB_LOGISTICS("Логистика", MeasureColumn.LOGISTICS),
    WB_STORAGE("Хранение", MeasureColumn.STORAGE),
    WB_ACCEPTANCE("Обработка товара", MeasureColumn.ACCEPTANCE),
    WB_PENALTY("Штраф", MeasureColumn.PENALTIES),
    WB_DEDUCTION("Удержания", MeasureColumn.OTHER),
    WB_COMPENSATION("Компенсация ущерба", MeasureColumn.COMPENSATION),
    WB_VOLUNTARY_COMPENSATION("Добровольная компенсация при возврате", MeasureColumn.COMPENSATION),
    WB_CORRECTION_SALES("Коррекция продаж", MeasureColumn.REVENUE),
    WB_CORRECTION_LOGISTICS("Коррекция логистики", MeasureColumn.LOGISTICS),
    WB_CORRECTION_ACQUIRING("Коррекция эквайринга", MeasureColumn.ACQUIRING),

    OTHER("OTHER", MeasureColumn.OTHER);

    /**
     * Canonical measure columns in {@code canonical_finance_entry} / {@code fact_finance}.
     * Each entry type maps to exactly one primary measure column.
     */
    public enum MeasureColumn {
        REVENUE,
        REFUND,
        MARKETPLACE_COMMISSION,
        ACQUIRING,
        LOGISTICS,
        STORAGE,
        PENALTIES,
        ACCEPTANCE,
        MARKETING,
        OTHER,
        COMPENSATION
    }

    private final String providerCode;
    private final MeasureColumn primaryMeasure;

    private static final Map<String, FinanceEntryType> OZON_LOOKUP =
            Stream.of(values())
                    .filter(t -> !t.name().startsWith("WB_") && t != OTHER)
                    .collect(Collectors.toMap(
                            FinanceEntryType::getProviderCode,
                            t -> t,
                            (a, b) -> a));

    private static final Map<String, FinanceEntryType> WB_LOOKUP =
            Stream.of(values())
                    .filter(t -> t.name().startsWith("WB_"))
                    .collect(Collectors.toMap(
                            FinanceEntryType::getProviderCode,
                            t -> t,
                            (a, b) -> a));

    FinanceEntryType(String providerCode, MeasureColumn primaryMeasure) {
        this.providerCode = providerCode;
        this.primaryMeasure = primaryMeasure;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public MeasureColumn primaryMeasure() {
        return primaryMeasure;
    }

    /**
     * Returns the canonical entry type name stored in {@code canonical_finance_entry.entry_type}.
     * WB-specific types are mapped to their canonical equivalents; Ozon types and aliases
     * are mapped to their canonical counterpart.
     */
    public String canonicalName() {
        return switch (this) {
            case WB_SALE, WB_CORRECTION_SALES -> SALE_ACCRUAL.name();
            case WB_RETURN -> RETURN_REVERSAL.name();
            case WB_LOGISTICS, WB_CORRECTION_LOGISTICS -> "DELIVERY";
            case WB_STORAGE -> STORAGE.name();
            case WB_ACCEPTANCE -> "ACCEPTANCE";
            case WB_PENALTY -> "PENALTY";
            case WB_DEDUCTION -> OTHER.name();
            case WB_COMPENSATION, WB_VOLUNTARY_COMPENSATION -> COMPENSATION.name();
            case WB_CORRECTION_ACQUIRING -> ACQUIRING.name();
            case DISPOSAL_DAMAGED -> DISPOSAL.name();
            case COMPENSATION_WITHOUT_DOCS, SELLER_COMPENSATION -> COMPENSATION.name();
            case REVIEWS_POINTS -> REVIEWS_PURCHASE.name();
            case SUBSCRIPTION_ALIAS -> SUBSCRIPTION.name();
            default -> this.name();
        };
    }

    /**
     * Whether this entry type has a per-field breakdown (accruals_for_sale + sale_commission + services[]).
     * Only SALE_ACCRUAL, RETURN_REVERSAL, and STORNO_CORRECTION have breakdowns in Ozon finance.
     */
    public boolean hasOzonBreakdown() {
        return this == SALE_ACCRUAL || this == RETURN_REVERSAL || this == STORNO_CORRECTION;
    }

    public static FinanceEntryType fromOzonOperationType(String operationType) {
        if (operationType == null) {
            return OTHER;
        }
        return OZON_LOOKUP.getOrDefault(operationType, OTHER);
    }

    public static FinanceEntryType fromWbDocTypeName(String docTypeName) {
        if (docTypeName == null || docTypeName.isBlank()) {
            return OTHER;
        }
        return WB_LOOKUP.getOrDefault(docTypeName.trim(), OTHER);
    }
}
