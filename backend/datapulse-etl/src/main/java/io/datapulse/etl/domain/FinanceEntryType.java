package io.datapulse.etl.domain;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Unified finance entry type enum mapping Ozon operation_type values and WB doc_type_name.
 * 44 Ozon types (23 empirical + 21 from official enum) + WB composite types.
 */
public enum FinanceEntryType {

    // --- Ozon empirical (23 types, confirmed from real API) ---
    SALE_ACCRUAL("OperationAgentDeliveredToCustomer"),
    RETURN_REVERSAL("ClientReturnAgentOperation"),
    STORNO_CORRECTION("OperationAgentStornoDeliveredToCustomer"),
    RETURN_LOGISTICS("OperationItemReturn"),
    ACQUIRING("MarketplaceRedistributionOfAcquiringOperation"),
    BRAND_COMMISSION("MarketplaceServiceBrandCommission"),
    LOGISTICS("MarketplaceServiceItemCrossdocking"),
    PACKAGING("OperationElectronicServiceStencil"),
    STORAGE("OperationMarketplaceServiceStorage"),
    SUBSCRIPTION("StarsMembership"),
    REVIEWS_PURCHASE("MarketplaceSaleReviewsOperation"),
    DISPOSAL("DisposalReasonFailedToPickupOnTime"),
    DISPOSAL_DAMAGED("DisposalReasonDamagedPackaging"),
    COMPENSATION("AccrualInternalClaim"),
    COMPENSATION_WITHOUT_DOCS("AccrualWithoutDocs"),
    SELLER_COMPENSATION("MarketplaceSellerCompensationOperation"),
    FBS_RETURN_LOGISTICS("OperationReturnGoodsFBSofRMS"),
    CPC_ADVERTISING("OperationMarketplaceCostPerClick"),
    PROMO_CPC("OperationPromotionWithCostPerOrder"),
    DEFECT_PENALTY("DefectRateCancellation"),
    REVIEWS_POINTS("OperationPointsForReviews"),
    SHIPMENT_DELAY_FINE("DefectFineShipmentDelayRated"),
    CANCELLATION_FINE("DefectFineCancellation"),

    // --- Ozon official enum (21 types, C-docs, not yet observed) ---
    DELIVERY_CANCEL_ACCRUAL("OperationAgentDeliveredToCustomerCanceled"),
    CLAIM("OperationClaim"),
    CORRECTION("OperationCorrectionSeller"),
    DEFECTIVE_WRITEOFF("OperationDefectiveWriteOff"),
    LACK_WRITEOFF("OperationLackWriteOff"),
    SETOFF("OperationSetOff"),
    CROSSDOCK_LOGISTICS("OperationMarketplaceCrossDockServiceWriteOff"),
    RFBS_RETURN("ReturnAgentOperationRFBS"),
    REEXPOSURE_DELIVERY("MarketplaceSellerReexposureDeliveryReturnOperation"),
    SHIPPING_COMPENSATION("MarketplaceSellerShippingCompensationReturnOperation"),
    MARKETING_ACTION("MarketplaceMarketingActionCostOperation"),
    PREMIUM_CASHBACK("OperationMarketplaceServicePremiumCashback"),
    PREMIUM_PROMOTION("MarketplaceServicePremiumPromotion"),
    PREMIUM_SELLER_BONUS("MarketplaceServicePremiumCashbackIndividualPoints"),
    PREMIUM_SUBSCRIPTION("OperationSubscriptionPremium"),
    FBS_RETURN_STORAGE_PVZ("MarketplaceReturnStorageServiceAtThePickupPointFbsItem"),
    FBS_RETURN_STORAGE_WH("MarketplaceReturnStorageServiceInTheWarehouseFbsItem"),
    KGT_LOGISTICS("MarketplaceServiceItemDeliveryKGT"),
    WITHHOLDING_UNDELIVERABLE("OperationMarketplaceWithHoldingForUndeliverableGoods"),
    SEARCH_PROMOTION("OperationElectronicServicesPromotionInSearch"),
    BRAND_SHELF("OperationMarketplaceServiceItemElectronicServicesBrandShelf"),

    // --- Alias from official enum (ItemAgentServiceStarsMembership = StarsMembership) ---
    SUBSCRIPTION_ALIAS("ItemAgentServiceStarsMembership"),

    // --- WB composite types (entry_type from doc_type_name) ---
    WB_SALE("Продажа"),
    WB_RETURN("Возврат"),
    WB_LOGISTICS("Логистика"),
    WB_STORAGE("Хранение"),
    WB_ACCEPTANCE("Обработка товара"),
    WB_PENALTY("Штраф"),
    WB_DEDUCTION("Удержания"),
    WB_COMPENSATION("Компенсация ущерба"),
    WB_VOLUNTARY_COMPENSATION("Добровольная компенсация при возврате"),
    WB_CORRECTION_SALES("Коррекция продаж"),
    WB_CORRECTION_LOGISTICS("Коррекция логистики"),
    WB_CORRECTION_ACQUIRING("Коррекция эквайринга"),

    OTHER("OTHER");

    private final String providerCode;

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

    FinanceEntryType(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getProviderCode() {
        return providerCode;
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
