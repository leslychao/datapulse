package io.datapulse.etl.adapter.ozon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import io.datapulse.etl.adapter.ozon.dto.OzonFinanceItem;
import io.datapulse.etl.adapter.ozon.dto.OzonFinancePosting;
import io.datapulse.etl.adapter.ozon.dto.OzonFinanceService;
import io.datapulse.etl.adapter.ozon.dto.OzonFinanceTransaction;
import io.datapulse.etl.domain.FinanceEntryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OzonFinanceNormalizerTest {

  private final OzonFinanceNormalizer normalizer = new OzonFinanceNormalizer();

  @Nested
  @DisplayName("normalizeFinanceTransaction()")
  class NormalizeFinanceTransaction {

    @Test
    void should_mapSaleAccrual_with_breakdown() {
      var tx = new OzonFinanceTransaction(
          1001L, "OperationAgentDeliveredToCustomer",
          "2024-01-15 10:30:00", "Доставка покупателю",
          BigDecimal.valueOf(1000), BigDecimal.valueOf(-150),
          BigDecimal.valueOf(700), "orders",
          new OzonFinancePosting(
              "FBO", "2024-01-14", "87621408-0010-1", 777L),
          List.of(
              new OzonFinanceService(
                  "MarketplaceServiceItemDirectFlowLogistic", BigDecimal.valueOf(-80)),
              new OzonFinanceService(
                  "MarketplaceRedistributionOfAcquiringOperation", BigDecimal.valueOf(-20))),
          List.of(new OzonFinanceItem("Product", 12345L)));

      var result = normalizer.normalizeFinanceTransaction(tx);

      assertThat(result.entryType()).isEqualTo(FinanceEntryType.SALE_ACCRUAL);
      assertThat(result.revenueAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
      assertThat(result.marketplaceCommissionAmount())
          .isEqualByComparingTo(BigDecimal.valueOf(-150));
      assertThat(result.logisticsCostAmount()).isEqualByComparingTo(BigDecimal.valueOf(-80));
      assertThat(result.acquiringCommissionAmount())
          .isEqualByComparingTo(BigDecimal.valueOf(-20));
      assertThat(result.postingId()).isEqualTo("87621408-0010-1");
      assertThat(result.orderId()).isEqualTo("87621408-0010");
      assertThat(result.marketplaceSku()).isEqualTo("12345");
      assertThat(result.warehouseExternalId()).isEqualTo("777");
    }

    @Test
    void should_mapReturnReversal_to_refund() {
      var tx = new OzonFinanceTransaction(
          1002L, "ClientReturnAgentOperation",
          "2024-01-20 14:00:00", "Возврат",
          BigDecimal.valueOf(-500), BigDecimal.ZERO,
          BigDecimal.valueOf(-500), "returns",
          new OzonFinancePosting(
              "FBO", "2024-01-14", "87621408-0010-1", 777L),
          null,
          List.of(new OzonFinanceItem("Product", 12345L)));

      var result = normalizer.normalizeFinanceTransaction(tx);

      assertThat(result.entryType()).isEqualTo(FinanceEntryType.RETURN_REVERSAL);
      assertThat(result.refundAmount()).isEqualByComparingTo(BigDecimal.valueOf(-500));
      assertThat(result.revenueAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_mapStandaloneStorage() {
      var tx = new OzonFinanceTransaction(
          1003L, "OperationMarketplaceServiceStorage",
          "2024-01-25 09:00:00", "Хранение",
          null, null,
          BigDecimal.valueOf(-200), "services",
          null, null, null);

      var result = normalizer.normalizeFinanceTransaction(tx);

      assertThat(result.entryType()).isEqualTo(FinanceEntryType.STORAGE);
      assertThat(result.storageCostAmount()).isEqualByComparingTo(BigDecimal.valueOf(-200));
      assertThat(result.revenueAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_mapUnknownOperationType_to_OTHER() {
      var tx = new OzonFinanceTransaction(
          1004L, "SomeNewOperation",
          "2024-01-25 09:00:00", "Unknown",
          null, null,
          BigDecimal.valueOf(-50), "other",
          null, null, null);

      var result = normalizer.normalizeFinanceTransaction(tx);

      assertThat(result.entryType()).isEqualTo(FinanceEntryType.OTHER);
    }

    @Test
    void should_resolvePostingId_from_postingFormat() {
      var tx = new OzonFinanceTransaction(
          1005L, "OperationAgentDeliveredToCustomer",
          "2024-01-15 10:30:00", "Доставка",
          BigDecimal.valueOf(500), BigDecimal.ZERO,
          BigDecimal.valueOf(500), "orders",
          new OzonFinancePosting(
              "FBO", "2024-01-14", "87621408-0010-1", 0L),
          null, null);

      var result = normalizer.normalizeFinanceTransaction(tx);

      assertThat(result.postingId()).isEqualTo("87621408-0010-1");
      assertThat(result.orderId()).isEqualTo("87621408-0010");
    }

    @Test
    void should_returnNullPostingId_when_acquiringFormat() {
      var tx = new OzonFinanceTransaction(
          1006L, "MarketplaceRedistributionOfAcquiringOperation",
          "2024-01-15 10:30:00", "Эквайринг",
          null, null,
          BigDecimal.valueOf(-20), "services",
          new OzonFinancePosting(
              "FBO", "2024-01-14", "87621408-0010", 0L),
          null, null);

      var result = normalizer.normalizeFinanceTransaction(tx);

      assertThat(result.postingId()).isNull();
      assertThat(result.orderId()).isEqualTo("87621408-0010");
    }

    @Test
    void should_returnNullBoth_when_noPosting() {
      var tx = new OzonFinanceTransaction(
          1007L, "OperationMarketplaceServiceStorage",
          "2024-01-25 09:00:00", "Хранение",
          null, null,
          BigDecimal.valueOf(-100), "services",
          null, null, null);

      var result = normalizer.normalizeFinanceTransaction(tx);

      assertThat(result.postingId()).isNull();
      assertThat(result.orderId()).isNull();
    }

    @Test
    void should_extractMarketplaceSku_from_items() {
      var tx = new OzonFinanceTransaction(
          1008L, "OperationAgentDeliveredToCustomer",
          "2024-01-15 10:30:00", "Доставка",
          BigDecimal.valueOf(500), BigDecimal.ZERO,
          BigDecimal.valueOf(500), "orders",
          null, null,
          List.of(new OzonFinanceItem("Product", 12345L)));

      var result = normalizer.normalizeFinanceTransaction(tx);

      assertThat(result.marketplaceSku()).isEqualTo("12345");
    }

    @Test
    void should_handleNullAmount() {
      var tx = new OzonFinanceTransaction(
          1009L, "OperationMarketplaceServiceStorage",
          "2024-01-25 09:00:00", "Хранение",
          null, null,
          null, "services",
          null, null, null);

      assertThatCode(() -> normalizer.normalizeFinanceTransaction(tx))
          .doesNotThrowAnyException();

      var result = normalizer.normalizeFinanceTransaction(tx);
      assertThat(result.netPayout()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    static Stream<Arguments> allOzonOperationTypes() {
      return Stream.of(
          Arguments.of("OperationAgentDeliveredToCustomer", FinanceEntryType.SALE_ACCRUAL),
          Arguments.of("ClientReturnAgentOperation", FinanceEntryType.RETURN_REVERSAL),
          Arguments.of("OperationAgentStornoDeliveredToCustomer",
              FinanceEntryType.STORNO_CORRECTION),
          Arguments.of("OperationItemReturn", FinanceEntryType.RETURN_LOGISTICS),
          Arguments.of("MarketplaceRedistributionOfAcquiringOperation",
              FinanceEntryType.ACQUIRING),
          Arguments.of("MarketplaceServiceBrandCommission",
              FinanceEntryType.BRAND_COMMISSION),
          Arguments.of("MarketplaceServiceItemCrossdocking", FinanceEntryType.LOGISTICS),
          Arguments.of("OperationElectronicServiceStencil", FinanceEntryType.PACKAGING),
          Arguments.of("OperationMarketplaceServiceStorage", FinanceEntryType.STORAGE),
          Arguments.of("StarsMembership", FinanceEntryType.SUBSCRIPTION),
          Arguments.of("MarketplaceSaleReviewsOperation",
              FinanceEntryType.REVIEWS_PURCHASE),
          Arguments.of("DisposalReasonFailedToPickupOnTime", FinanceEntryType.DISPOSAL),
          Arguments.of("DisposalReasonDamagedPackaging",
              FinanceEntryType.DISPOSAL_DAMAGED),
          Arguments.of("AccrualInternalClaim", FinanceEntryType.COMPENSATION),
          Arguments.of("AccrualWithoutDocs",
              FinanceEntryType.COMPENSATION_WITHOUT_DOCS),
          Arguments.of("MarketplaceSellerCompensationOperation",
              FinanceEntryType.SELLER_COMPENSATION),
          Arguments.of("OperationReturnGoodsFBSofRMS",
              FinanceEntryType.FBS_RETURN_LOGISTICS),
          Arguments.of("OperationMarketplaceCostPerClick",
              FinanceEntryType.CPC_ADVERTISING),
          Arguments.of("OperationPromotionWithCostPerOrder", FinanceEntryType.PROMO_CPC),
          Arguments.of("DefectRateCancellation", FinanceEntryType.DEFECT_PENALTY),
          Arguments.of("OperationPointsForReviews", FinanceEntryType.REVIEWS_POINTS),
          Arguments.of("DefectFineShipmentDelayRated",
              FinanceEntryType.SHIPMENT_DELAY_FINE),
          Arguments.of("DefectFineCancellation", FinanceEntryType.CANCELLATION_FINE),
          Arguments.of("InsuranceServiceSellerItem", FinanceEntryType.INSURANCE_SELLER),
          Arguments.of("OperationGettingToTheTop", FinanceEntryType.GETTING_TO_TOP),
          Arguments.of("OperationMarketplaceServiceSupplyInboundCargoShortage",
              FinanceEntryType.SUPPLY_CARGO_SHORTAGE),
          Arguments.of("DefectRateDetailed", FinanceEntryType.DEFECT_RATE_DETAILED),
          Arguments.of("TemporaryStorage", FinanceEntryType.TEMPORARY_STORAGE),
          Arguments.of("OperationAgentDeliveredToCustomerCanceled",
              FinanceEntryType.DELIVERY_CANCEL_ACCRUAL),
          Arguments.of("OperationClaim", FinanceEntryType.CLAIM),
          Arguments.of("OperationCorrectionSeller", FinanceEntryType.CORRECTION),
          Arguments.of("OperationDefectiveWriteOff", FinanceEntryType.DEFECTIVE_WRITEOFF),
          Arguments.of("OperationLackWriteOff", FinanceEntryType.LACK_WRITEOFF),
          Arguments.of("OperationSetOff", FinanceEntryType.SETOFF),
          Arguments.of("OperationMarketplaceCrossDockServiceWriteOff",
              FinanceEntryType.CROSSDOCK_LOGISTICS),
          Arguments.of("ReturnAgentOperationRFBS", FinanceEntryType.RFBS_RETURN),
          Arguments.of("MarketplaceSellerReexposureDeliveryReturnOperation",
              FinanceEntryType.REEXPOSURE_DELIVERY),
          Arguments.of("MarketplaceSellerShippingCompensationReturnOperation",
              FinanceEntryType.SHIPPING_COMPENSATION),
          Arguments.of("MarketplaceMarketingActionCostOperation",
              FinanceEntryType.MARKETING_ACTION),
          Arguments.of("OperationMarketplaceServicePremiumCashback",
              FinanceEntryType.PREMIUM_CASHBACK),
          Arguments.of("MarketplaceServicePremiumPromotion",
              FinanceEntryType.PREMIUM_PROMOTION),
          Arguments.of("MarketplaceServicePremiumCashbackIndividualPoints",
              FinanceEntryType.PREMIUM_SELLER_BONUS),
          Arguments.of("OperationSubscriptionPremium",
              FinanceEntryType.PREMIUM_SUBSCRIPTION),
          Arguments.of("MarketplaceReturnStorageServiceAtThePickupPointFbsItem",
              FinanceEntryType.FBS_RETURN_STORAGE_PVZ),
          Arguments.of("MarketplaceReturnStorageServiceInTheWarehouseFbsItem",
              FinanceEntryType.FBS_RETURN_STORAGE_WH),
          Arguments.of("MarketplaceServiceItemDeliveryKGT",
              FinanceEntryType.KGT_LOGISTICS),
          Arguments.of("OperationMarketplaceWithHoldingForUndeliverableGoods",
              FinanceEntryType.WITHHOLDING_UNDELIVERABLE),
          Arguments.of("OperationElectronicServicesPromotionInSearch",
              FinanceEntryType.SEARCH_PROMOTION),
          Arguments.of("OperationMarketplaceServiceItemElectronicServicesBrandShelf",
              FinanceEntryType.BRAND_SHELF),
          Arguments.of("ItemAgentServiceStarsMembership",
              FinanceEntryType.SUBSCRIPTION_ALIAS),
          Arguments.of("AccrualConsigDefectiveWriteOff",
              FinanceEntryType.CONSIG_DEFECTIVE_WRITEOFF),
          Arguments.of("DefectFineShipmentDelay",
              FinanceEntryType.SHIPMENT_DELAY_FINE_FLAT),
          Arguments.of("MarketplaceSellerCorrectionOperation",
              FinanceEntryType.SELLER_CORRECTION),
          Arguments.of("MarketplaceSellerDecompensationItemByTypeDocOperation",
              FinanceEntryType.SELLER_DECOMPENSATION),
          Arguments.of("OperationMarketplaceServiceSupplyInboundCargoSurplus",
              FinanceEntryType.SUPPLY_CARGO_SURPLUS),
          Arguments.of("OperationMarketplaceSupplyAdditional",
              FinanceEntryType.SUPPLY_ADDITIONAL),
          Arguments.of("OperationSellerReturnsCargoAssortmentInvalid",
              FinanceEntryType.RETURNS_ASSORTMENT_INVALID),
          Arguments.of("SellerReturnsDeliveryToPickupPoint",
              FinanceEntryType.RETURNS_DELIVERY_TO_PVZ)
      );
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("allOzonOperationTypes")
    @DisplayName("All known Ozon operation types map correctly through normalizer")
    void should_mapAllKnown_ozonOperationTypes(String operationType,
        FinanceEntryType expected) {
      var tx = new OzonFinanceTransaction(
          2000L, operationType,
          "2024-01-15 10:30:00", "desc",
          null, null,
          BigDecimal.valueOf(-100), "services",
          null, null, null);

      var result = normalizer.normalizeFinanceTransaction(tx);

      assertThat(result.entryType()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Negative amount goes to debit-side column")
    void should_routeNegativeAmount_to_correctDebitColumn() {
      var tx = new OzonFinanceTransaction(
          2001L, "OperationMarketplaceServiceStorage",
          "2024-01-15 10:30:00", "Хранение",
          null, null,
          BigDecimal.valueOf(-350), "services",
          null, null, null);

      var result = normalizer.normalizeFinanceTransaction(tx);

      assertThat(result.storageCostAmount())
          .as("storage gets the amount")
          .isEqualByComparingTo(BigDecimal.valueOf(-350));
      assertThat(result.revenueAmount())
          .as("revenue stays zero")
          .isEqualByComparingTo(BigDecimal.ZERO);
    }
  }
}
