package io.datapulse.etl.adapter.ozon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.util.List;

import io.datapulse.etl.adapter.ozon.dto.OzonFinanceItem;
import io.datapulse.etl.adapter.ozon.dto.OzonFinancePosting;
import io.datapulse.etl.adapter.ozon.dto.OzonFinanceService;
import io.datapulse.etl.adapter.ozon.dto.OzonFinanceTransaction;
import io.datapulse.etl.domain.FinanceEntryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
          BigDecimal.valueOf(1000), BigDecimal.valueOf(150),
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
  }
}
