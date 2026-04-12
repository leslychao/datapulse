package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FinanceEntryTypeTest {

  @Nested
  @DisplayName("fromOzonOperationType()")
  class FromOzonOperationType {

    static Stream<Arguments> ozonTypes() {
      return Stream.of(
          Arguments.of("OperationAgentDeliveredToCustomer", FinanceEntryType.SALE_ACCRUAL),
          Arguments.of("ClientReturnAgentOperation", FinanceEntryType.RETURN_REVERSAL),
          Arguments.of("OperationAgentStornoDeliveredToCustomer", FinanceEntryType.STORNO_CORRECTION),
          Arguments.of("OperationItemReturn", FinanceEntryType.RETURN_LOGISTICS),
          Arguments.of("MarketplaceRedistributionOfAcquiringOperation", FinanceEntryType.ACQUIRING),
          Arguments.of("MarketplaceServiceBrandCommission", FinanceEntryType.BRAND_COMMISSION),
          Arguments.of("MarketplaceServiceItemCrossdocking", FinanceEntryType.LOGISTICS),
          Arguments.of("OperationElectronicServiceStencil", FinanceEntryType.PACKAGING),
          Arguments.of("OperationMarketplaceServiceStorage", FinanceEntryType.STORAGE),
          Arguments.of("StarsMembership", FinanceEntryType.SUBSCRIPTION),
          Arguments.of("MarketplaceSaleReviewsOperation", FinanceEntryType.REVIEWS_PURCHASE),
          Arguments.of("DisposalReasonFailedToPickupOnTime", FinanceEntryType.DISPOSAL),
          Arguments.of("DisposalReasonDamagedPackaging", FinanceEntryType.DISPOSAL_DAMAGED),
          Arguments.of("AccrualInternalClaim", FinanceEntryType.COMPENSATION),
          Arguments.of("AccrualWithoutDocs", FinanceEntryType.COMPENSATION_WITHOUT_DOCS),
          Arguments.of("MarketplaceSellerCompensationOperation", FinanceEntryType.SELLER_COMPENSATION),
          Arguments.of("OperationReturnGoodsFBSofRMS", FinanceEntryType.FBS_RETURN_LOGISTICS),
          Arguments.of("OperationMarketplaceCostPerClick", FinanceEntryType.CPC_ADVERTISING),
          Arguments.of("OperationPromotionWithCostPerOrder", FinanceEntryType.PROMO_CPC),
          Arguments.of("DefectRateCancellation", FinanceEntryType.DEFECT_PENALTY),
          Arguments.of("OperationPointsForReviews", FinanceEntryType.REVIEWS_POINTS),
          Arguments.of("DefectFineShipmentDelayRated", FinanceEntryType.SHIPMENT_DELAY_FINE),
          Arguments.of("DefectFineCancellation", FinanceEntryType.CANCELLATION_FINE),
          Arguments.of("InsuranceServiceSellerItem", FinanceEntryType.INSURANCE_SELLER),
          Arguments.of("OperationGettingToTheTop", FinanceEntryType.GETTING_TO_TOP),
          Arguments.of("OperationMarketplaceServiceSupplyInboundCargoShortage", FinanceEntryType.SUPPLY_CARGO_SHORTAGE),
          Arguments.of("DefectRateDetailed", FinanceEntryType.DEFECT_RATE_DETAILED),
          Arguments.of("TemporaryStorage", FinanceEntryType.TEMPORARY_STORAGE),
          Arguments.of("OperationAgentDeliveredToCustomerCanceled", FinanceEntryType.DELIVERY_CANCEL_ACCRUAL),
          Arguments.of("OperationClaim", FinanceEntryType.CLAIM),
          Arguments.of("OperationCorrectionSeller", FinanceEntryType.CORRECTION),
          Arguments.of("OperationDefectiveWriteOff", FinanceEntryType.DEFECTIVE_WRITEOFF),
          Arguments.of("OperationLackWriteOff", FinanceEntryType.LACK_WRITEOFF),
          Arguments.of("OperationSetOff", FinanceEntryType.SETOFF),
          Arguments.of("OperationMarketplaceCrossDockServiceWriteOff", FinanceEntryType.CROSSDOCK_LOGISTICS),
          Arguments.of("ReturnAgentOperationRFBS", FinanceEntryType.RFBS_RETURN),
          Arguments.of("MarketplaceSellerReexposureDeliveryReturnOperation", FinanceEntryType.REEXPOSURE_DELIVERY),
          Arguments.of("MarketplaceSellerShippingCompensationReturnOperation", FinanceEntryType.SHIPPING_COMPENSATION),
          Arguments.of("MarketplaceMarketingActionCostOperation", FinanceEntryType.MARKETING_ACTION),
          Arguments.of("OperationMarketplaceServicePremiumCashback", FinanceEntryType.PREMIUM_CASHBACK),
          Arguments.of("MarketplaceServicePremiumPromotion", FinanceEntryType.PREMIUM_PROMOTION),
          Arguments.of("MarketplaceServicePremiumCashbackIndividualPoints", FinanceEntryType.PREMIUM_SELLER_BONUS),
          Arguments.of("OperationSubscriptionPremium", FinanceEntryType.PREMIUM_SUBSCRIPTION),
          Arguments.of("MarketplaceReturnStorageServiceAtThePickupPointFbsItem", FinanceEntryType.FBS_RETURN_STORAGE_PVZ),
          Arguments.of("MarketplaceReturnStorageServiceInTheWarehouseFbsItem", FinanceEntryType.FBS_RETURN_STORAGE_WH),
          Arguments.of("MarketplaceServiceItemDeliveryKGT", FinanceEntryType.KGT_LOGISTICS),
          Arguments.of("OperationMarketplaceWithHoldingForUndeliverableGoods", FinanceEntryType.WITHHOLDING_UNDELIVERABLE),
          Arguments.of("OperationElectronicServicesPromotionInSearch", FinanceEntryType.SEARCH_PROMOTION),
          Arguments.of("OperationMarketplaceServiceItemElectronicServicesBrandShelf", FinanceEntryType.BRAND_SHELF),
          Arguments.of("ItemAgentServiceStarsMembership", FinanceEntryType.SUBSCRIPTION_ALIAS),
          Arguments.of("AccrualConsigDefectiveWriteOff", FinanceEntryType.CONSIG_DEFECTIVE_WRITEOFF),
          Arguments.of("DefectFineShipmentDelay", FinanceEntryType.SHIPMENT_DELAY_FINE_FLAT),
          Arguments.of("MarketplaceSellerCorrectionOperation", FinanceEntryType.SELLER_CORRECTION),
          Arguments.of("MarketplaceSellerDecompensationItemByTypeDocOperation", FinanceEntryType.SELLER_DECOMPENSATION),
          Arguments.of("OperationMarketplaceServiceSupplyInboundCargoSurplus", FinanceEntryType.SUPPLY_CARGO_SURPLUS),
          Arguments.of("OperationMarketplaceSupplyAdditional", FinanceEntryType.SUPPLY_ADDITIONAL),
          Arguments.of("OperationSellerReturnsCargoAssortmentInvalid", FinanceEntryType.RETURNS_ASSORTMENT_INVALID),
          Arguments.of("SellerReturnsDeliveryToPickupPoint", FinanceEntryType.RETURNS_DELIVERY_TO_PVZ)
      );
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("ozonTypes")
    void should_mapKnownOzonType(String providerCode, FinanceEntryType expected) {
      assertThat(FinanceEntryType.fromOzonOperationType(providerCode)).isEqualTo(expected);
    }

    @Test
    void should_returnOther_when_unknownOzonType() {
      assertThat(FinanceEntryType.fromOzonOperationType("SomeNewType"))
          .isEqualTo(FinanceEntryType.OTHER);
    }

    @Test
    void should_returnOther_when_null() {
      assertThat(FinanceEntryType.fromOzonOperationType(null))
          .isEqualTo(FinanceEntryType.OTHER);
    }
  }

  @Nested
  @DisplayName("fromWbSupplierOperName()")
  class FromWbSupplierOperName {

    static Stream<Arguments> wbTypes() {
      return Stream.of(
          Arguments.of("Продажа", FinanceEntryType.WB_SALE),
          Arguments.of("Возврат", FinanceEntryType.WB_RETURN),
          Arguments.of("Логистика", FinanceEntryType.WB_LOGISTICS),
          Arguments.of("Хранение", FinanceEntryType.WB_STORAGE),
          Arguments.of("Обработка товара", FinanceEntryType.WB_ACCEPTANCE),
          Arguments.of("Штраф", FinanceEntryType.WB_PENALTY),
          Arguments.of("Удержания", FinanceEntryType.WB_DEDUCTION),
          Arguments.of("Компенсация ущерба", FinanceEntryType.WB_COMPENSATION),
          Arguments.of("Добровольная компенсация при возврате", FinanceEntryType.WB_VOLUNTARY_COMPENSATION),
          Arguments.of("Коррекция продаж", FinanceEntryType.WB_CORRECTION_SALES),
          Arguments.of("Коррекция логистики", FinanceEntryType.WB_CORRECTION_LOGISTICS),
          Arguments.of("Коррекция эквайринга", FinanceEntryType.WB_CORRECTION_ACQUIRING),
          Arguments.of("Возмещение издержек по перевозке", FinanceEntryType.WB_TRANSPORT_REIMBURSEMENT),
          Arguments.of("Возмещение за выдачу и возврат товаров на ПВЗ", FinanceEntryType.WB_PVZ_REIMBURSEMENT),
          Arguments.of("Услуга платной доставки", FinanceEntryType.WB_PAID_DELIVERY),
          Arguments.of("Бронирование товара через самовывоз", FinanceEntryType.WB_CLICK_COLLECT),
          Arguments.of("Стоимость участия в программе лояльности", FinanceEntryType.WB_LOYALTY_FEE),
          Arguments.of("Сумма, удержанная за начисленные баллы программы лояльности", FinanceEntryType.WB_LOYALTY_DEDUCTION),
          Arguments.of("Компенсация скидки по программе лояльности", FinanceEntryType.WB_LOYALTY_COMPENSATION),
          Arguments.of("Разовое изменение срока перечисления денежных средств", FinanceEntryType.WB_EARLY_WITHDRAWAL)
      );
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("wbTypes")
    void should_mapKnownWbType(String supplierOperName, FinanceEntryType expected) {
      assertThat(FinanceEntryType.fromWbSupplierOperName(supplierOperName)).isEqualTo(expected);
    }

    @Test
    void should_returnOther_when_unknownWbType() {
      assertThat(FinanceEntryType.fromWbSupplierOperName("Неизвестная операция"))
          .isEqualTo(FinanceEntryType.OTHER);
    }

    @Test
    void should_returnOther_when_null() {
      assertThat(FinanceEntryType.fromWbSupplierOperName(null))
          .isEqualTo(FinanceEntryType.OTHER);
    }

    @Test
    void should_returnOther_when_blank() {
      assertThat(FinanceEntryType.fromWbSupplierOperName("  "))
          .isEqualTo(FinanceEntryType.OTHER);
    }

    @Test
    void should_trimWhitespace() {
      assertThat(FinanceEntryType.fromWbSupplierOperName("  Продажа  "))
          .isEqualTo(FinanceEntryType.WB_SALE);
    }
  }

  @Nested
  @DisplayName("fromYandexServiceName()")
  class FromYandexServiceName {

    static Stream<Arguments> yandexServiceNames() {
      return Stream.of(
          Arguments.of("Размещение заказа", FinanceEntryType.YANDEX_COMMISSION),
          Arguments.of("Комиссия за продажу", FinanceEntryType.YANDEX_COMMISSION),
          Arguments.of("Fee for order placement", FinanceEntryType.YANDEX_COMMISSION),
          Arguments.of("Продвижение товара", FinanceEntryType.YANDEX_ADVERTISING),
          Arguments.of("Boost campaign", FinanceEntryType.YANDEX_ADVERTISING),
          Arguments.of("Логистика прямая", FinanceEntryType.YANDEX_LOGISTICS),
          Arguments.of("Доставка покупателю", FinanceEntryType.YANDEX_LOGISTICS),
          Arguments.of("Обратная логистика возврата", FinanceEntryType.YANDEX_RETURN_LOGISTICS),
          Arguments.of("Return delivery service", FinanceEntryType.YANDEX_RETURN_LOGISTICS),
          Arguments.of("Хранение на складе", FinanceEntryType.YANDEX_STORAGE),
          Arguments.of("Storage fee", FinanceEntryType.YANDEX_STORAGE),
          Arguments.of("Размещение товаров на витрине", FinanceEntryType.YANDEX_PLACEMENT),
          Arguments.of("Placement of goods in showcase", FinanceEntryType.YANDEX_PLACEMENT),
          Arguments.of("Перечисление оплаты", FinanceEntryType.YANDEX_ACQUIRING),
          Arguments.of("Transfer of payment to seller", FinanceEntryType.YANDEX_ACQUIRING),
          Arguments.of("Штраф за просрочку", FinanceEntryType.YANDEX_PENALTY),
          Arguments.of("Penalty for late delivery", FinanceEntryType.YANDEX_PENALTY),
          Arguments.of("Отмена заказа", FinanceEntryType.YANDEX_CANCELLATION),
          Arguments.of("Cancellation fee", FinanceEntryType.YANDEX_CANCELLATION),
          Arguments.of("Субсидия маркетплейса", FinanceEntryType.YANDEX_SUBSIDY),
          Arguments.of("Subsidy from marketplace", FinanceEntryType.YANDEX_SUBSIDY),
          Arguments.of("Компенсация расходов", FinanceEntryType.YANDEX_SUBSIDY)
      );
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @MethodSource("yandexServiceNames")
    void should_mapKnownYandexServiceName(String serviceName, FinanceEntryType expected) {
      assertThat(FinanceEntryType.fromYandexServiceName(serviceName)).isEqualTo(expected);
    }

    @Test
    void should_returnOther_when_unknownYandexServiceName() {
      assertThat(FinanceEntryType.fromYandexServiceName("Новая услуга"))
          .isEqualTo(FinanceEntryType.OTHER);
    }

    @Test
    void should_returnOther_when_nullYandexServiceName() {
      assertThat(FinanceEntryType.fromYandexServiceName(null))
          .isEqualTo(FinanceEntryType.OTHER);
    }

    @Test
    void should_returnOther_when_blankYandexServiceName() {
      assertThat(FinanceEntryType.fromYandexServiceName("   "))
          .isEqualTo(FinanceEntryType.OTHER);
    }
  }

  @Nested
  @DisplayName("canonicalName()")
  class CanonicalName {

    @Test
    void should_mapWbSale_to_SALE_ACCRUAL() {
      assertThat(FinanceEntryType.WB_SALE.canonicalName()).isEqualTo("SALE_ACCRUAL");
    }

    @Test
    void should_mapWbReturn_to_RETURN_REVERSAL() {
      assertThat(FinanceEntryType.WB_RETURN.canonicalName()).isEqualTo("RETURN_REVERSAL");
    }

    @Test
    void should_mapWbLogistics_to_DELIVERY() {
      assertThat(FinanceEntryType.WB_LOGISTICS.canonicalName()).isEqualTo("DELIVERY");
    }

    @Test
    void should_mapOzonType_to_itsOwnName() {
      assertThat(FinanceEntryType.SALE_ACCRUAL.canonicalName()).isEqualTo("SALE_ACCRUAL");
      assertThat(FinanceEntryType.STORAGE.canonicalName()).isEqualTo("STORAGE");
    }

    @Test
    void should_mapAliases_to_canonical() {
      assertThat(FinanceEntryType.SUBSCRIPTION_ALIAS.canonicalName())
          .isEqualTo("SUBSCRIPTION");
      assertThat(FinanceEntryType.DISPOSAL_DAMAGED.canonicalName())
          .isEqualTo("DISPOSAL");
    }

    @Test
    void should_mapYandexSale_to_SALE_ACCRUAL() {
      assertThat(FinanceEntryType.YANDEX_SALE.canonicalName()).isEqualTo("SALE_ACCRUAL");
    }

    @Test
    void should_mapYandexCommission_to_MARKETPLACE_COMMISSION() {
      assertThat(FinanceEntryType.YANDEX_COMMISSION.canonicalName())
          .isEqualTo("MARKETPLACE_COMMISSION");
    }

    @Test
    void should_mapYandexLogistics_to_DELIVERY() {
      assertThat(FinanceEntryType.YANDEX_LOGISTICS.canonicalName()).isEqualTo("DELIVERY");
      assertThat(FinanceEntryType.YANDEX_RETURN_LOGISTICS.canonicalName())
          .isEqualTo("DELIVERY");
    }

    @Test
    void should_mapYandexPenalty_to_PENALTY() {
      assertThat(FinanceEntryType.YANDEX_PENALTY.canonicalName()).isEqualTo("PENALTY");
      assertThat(FinanceEntryType.YANDEX_CANCELLATION.canonicalName()).isEqualTo("PENALTY");
    }
  }

  @Nested
  @DisplayName("primaryMeasure()")
  class PrimaryMeasure {

    @Test
    void should_returnRevenue_for_sale() {
      assertThat(FinanceEntryType.SALE_ACCRUAL.primaryMeasure())
          .isEqualTo(FinanceEntryType.MeasureColumn.REVENUE);
    }

    @Test
    void should_returnLogistics_for_logistics() {
      assertThat(FinanceEntryType.LOGISTICS.primaryMeasure())
          .isEqualTo(FinanceEntryType.MeasureColumn.LOGISTICS);
    }

    @Test
    void should_returnMarketing_for_cpc() {
      assertThat(FinanceEntryType.CPC_ADVERTISING.primaryMeasure())
          .isEqualTo(FinanceEntryType.MeasureColumn.MARKETING);
    }

    @Test
    void should_returnPenalties_for_fines() {
      assertThat(FinanceEntryType.SHIPMENT_DELAY_FINE.primaryMeasure())
          .isEqualTo(FinanceEntryType.MeasureColumn.PENALTIES);
    }
  }
}
