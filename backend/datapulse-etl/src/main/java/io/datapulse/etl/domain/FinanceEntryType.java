package io.datapulse.etl.domain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Unified finance entry type enum mapping Ozon operation_type values and WB doc_type_name.
 * 56 Ozon types (35 empirical + 21 from official enum) + WB composite types.
 *
 * <p>{@link #canonicalName()} maps WB-specific types to canonical entry type names
 * stored in {@code canonical_finance_entry.entry_type}.</p>
 *
 * <p>{@link #primaryMeasure()} returns the canonical measure column for the entry type,
 * used by Ozon normalizer to route {@code operation.amount} into the correct column.</p>
 */
public enum FinanceEntryType {

    // --- Ozon empirical (27 types, confirmed from real API) ---
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
    INSURANCE_SELLER("InsuranceServiceSellerItem", MeasureColumn.OTHER),
    GETTING_TO_TOP("OperationGettingToTheTop", MeasureColumn.MARKETING),
    SUPPLY_CARGO_SHORTAGE("OperationMarketplaceServiceSupplyInboundCargoShortage", MeasureColumn.PENALTIES),
    DEFECT_RATE_DETAILED("DefectRateDetailed", MeasureColumn.PENALTIES),
    TEMPORARY_STORAGE("TemporaryStorage", MeasureColumn.STORAGE),

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

    // --- Ozon empirical (8 types, discovered Apr 2026 from real API) ---
    CONSIG_DEFECTIVE_WRITEOFF("AccrualConsigDefectiveWriteOff", MeasureColumn.COMPENSATION),
    SHIPMENT_DELAY_FINE_FLAT("DefectFineShipmentDelay", MeasureColumn.PENALTIES),
    SELLER_CORRECTION("MarketplaceSellerCorrectionOperation", MeasureColumn.OTHER),
    SELLER_DECOMPENSATION("MarketplaceSellerDecompensationItemByTypeDocOperation", MeasureColumn.COMPENSATION),
    SUPPLY_CARGO_SURPLUS("OperationMarketplaceServiceSupplyInboundCargoSurplus", MeasureColumn.PENALTIES),
    SUPPLY_ADDITIONAL("OperationMarketplaceSupplyAdditional", MeasureColumn.LOGISTICS),
    RETURNS_ASSORTMENT_INVALID("OperationSellerReturnsCargoAssortmentInvalid", MeasureColumn.PENALTIES),
    RETURNS_DELIVERY_TO_PVZ("SellerReturnsDeliveryToPickupPoint", MeasureColumn.LOGISTICS),

    // --- WB composite types (entry_type from supplier_oper_name) ---
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

    // --- WB additional supplier_oper_name values (official docs, 2026-03-30) ---
    WB_TRANSPORT_REIMBURSEMENT("Возмещение издержек по перевозке", MeasureColumn.LOGISTICS),
    WB_PVZ_REIMBURSEMENT("Возмещение за выдачу и возврат товаров на ПВЗ", MeasureColumn.LOGISTICS),
    WB_PAID_DELIVERY("Услуга платной доставки", MeasureColumn.LOGISTICS),
    WB_CLICK_COLLECT("Бронирование товара через самовывоз", MeasureColumn.OTHER),
    WB_LOYALTY_FEE("Стоимость участия в программе лояльности", MeasureColumn.OTHER),
    WB_LOYALTY_DEDUCTION("Сумма, удержанная за начисленные баллы программы лояльности", MeasureColumn.OTHER),
    WB_LOYALTY_COMPENSATION("Компенсация скидки по программе лояльности", MeasureColumn.OTHER),
    WB_EARLY_WITHDRAWAL("Разовое изменение срока перечисления денежных средств", MeasureColumn.OTHER),

    // --- Yandex Market entry types (from service report serviceName) ---
    YANDEX_SALE("yandex_realization", MeasureColumn.REVENUE),
    YANDEX_COMMISSION("yandex_sale_commission", MeasureColumn.MARKETPLACE_COMMISSION),
    YANDEX_LOGISTICS("yandex_logistics", MeasureColumn.LOGISTICS),
    YANDEX_RETURN_LOGISTICS("yandex_return_logistics", MeasureColumn.LOGISTICS),
    YANDEX_STORAGE("yandex_storage", MeasureColumn.STORAGE),
    YANDEX_PLACEMENT("yandex_placement", MeasureColumn.OTHER),
    YANDEX_ACQUIRING("yandex_acquiring", MeasureColumn.ACQUIRING),
    YANDEX_PENALTY("yandex_penalty", MeasureColumn.PENALTIES),
    YANDEX_ADVERTISING("yandex_advertising", MeasureColumn.MARKETING),
    YANDEX_SUBSIDY("yandex_subsidy", MeasureColumn.COMPENSATION),
    YANDEX_CANCELLATION("yandex_cancellation", MeasureColumn.PENALTIES),

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
                    .filter(t -> !t.name().startsWith("WB_")
                        && !t.name().startsWith("YANDEX_")
                        && t != OTHER)
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
            case WB_LOGISTICS, WB_CORRECTION_LOGISTICS, WB_TRANSPORT_REIMBURSEMENT,
                    WB_PVZ_REIMBURSEMENT, WB_PAID_DELIVERY -> "DELIVERY";
            case WB_STORAGE -> STORAGE.name();
            case WB_ACCEPTANCE -> "ACCEPTANCE";
            case WB_PENALTY -> "PENALTY";
            case WB_DEDUCTION, WB_CLICK_COLLECT, WB_LOYALTY_FEE,
                    WB_LOYALTY_DEDUCTION, WB_LOYALTY_COMPENSATION,
                    WB_EARLY_WITHDRAWAL -> OTHER.name();
            case WB_COMPENSATION, WB_VOLUNTARY_COMPENSATION -> COMPENSATION.name();
            case WB_CORRECTION_ACQUIRING -> ACQUIRING.name();
            case DISPOSAL_DAMAGED -> DISPOSAL.name();
            case COMPENSATION_WITHOUT_DOCS, SELLER_COMPENSATION,
                    CONSIG_DEFECTIVE_WRITEOFF, SELLER_DECOMPENSATION -> COMPENSATION.name();
            case REVIEWS_POINTS -> REVIEWS_PURCHASE.name();
            case SUBSCRIPTION_ALIAS -> SUBSCRIPTION.name();
            case SHIPMENT_DELAY_FINE_FLAT -> SHIPMENT_DELAY_FINE.name();
            case SUPPLY_CARGO_SURPLUS -> SUPPLY_CARGO_SHORTAGE.name();
            case RETURNS_ASSORTMENT_INVALID -> DEFECT_PENALTY.name();
            case YANDEX_SALE -> SALE_ACCRUAL.name();
            case YANDEX_COMMISSION -> "MARKETPLACE_COMMISSION";
            case YANDEX_LOGISTICS, YANDEX_RETURN_LOGISTICS -> "DELIVERY";
            case YANDEX_STORAGE -> STORAGE.name();
            case YANDEX_ACQUIRING -> ACQUIRING.name();
            case YANDEX_PENALTY, YANDEX_CANCELLATION -> "PENALTY";
            case YANDEX_ADVERTISING -> "ADVERTISING";
            case YANDEX_PLACEMENT -> OTHER.name();
            case YANDEX_SUBSIDY -> COMPENSATION.name();
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

    public static FinanceEntryType fromWbSupplierOperName(String supplierOperName) {
        if (supplierOperName == null || supplierOperName.isBlank()) {
            return OTHER;
        }
        return WB_LOOKUP.getOrDefault(supplierOperName.trim(), OTHER);
    }

    /**
     * Pattern-based mapping for Yandex Market {@code serviceName} values.
     * Yandex report structure may change without notice, so matching is
     * keyword-based with fallback to {@link #OTHER}.
     */
    private static final List<Map.Entry<String, FinanceEntryType>> YANDEX_PATTERNS = List.of(
        Map.entry("Обратная логистика", YANDEX_RETURN_LOGISTICS),
        Map.entry("Return delivery", YANDEX_RETURN_LOGISTICS),
        Map.entry("Возврат", YANDEX_RETURN_LOGISTICS),
        Map.entry("Размещение заказа", YANDEX_COMMISSION),
        Map.entry("Комиссия", YANDEX_COMMISSION),
        Map.entry("Fee for order placement", YANDEX_COMMISSION),
        Map.entry("Логистика", YANDEX_LOGISTICS),
        Map.entry("Доставка", YANDEX_LOGISTICS),
        Map.entry("Продвижение", YANDEX_ADVERTISING),
        Map.entry("Boost", YANDEX_ADVERTISING),
        Map.entry("Хранение", YANDEX_STORAGE),
        Map.entry("Storage", YANDEX_STORAGE),
        Map.entry("Размещение товаров на витрине", YANDEX_PLACEMENT),
        Map.entry("Placement of goods", YANDEX_PLACEMENT),
        Map.entry("Перечисление", YANDEX_ACQUIRING),
        Map.entry("Transfer of payment", YANDEX_ACQUIRING),
        Map.entry("Штраф", YANDEX_PENALTY),
        Map.entry("Penalty", YANDEX_PENALTY),
        Map.entry("Отмена", YANDEX_CANCELLATION),
        Map.entry("Cancellation", YANDEX_CANCELLATION),
        Map.entry("Субсидия", YANDEX_SUBSIDY),
        Map.entry("Subsidy", YANDEX_SUBSIDY),
        Map.entry("Компенсация", YANDEX_SUBSIDY)
    );

    /**
     * Maps Yandex Market {@code serviceName} to entry type using keyword matching.
     * Falls back to {@link #OTHER} if no pattern matches.
     */
    public static FinanceEntryType fromYandexServiceName(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return OTHER;
        }
        String trimmed = serviceName.trim();
        for (var entry : YANDEX_PATTERNS) {
            if (trimmed.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return OTHER;
    }
}
