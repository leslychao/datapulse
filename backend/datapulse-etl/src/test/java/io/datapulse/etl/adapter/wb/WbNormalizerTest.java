package io.datapulse.etl.adapter.wb;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.etl.adapter.wb.dto.WbCatalogCard;
import io.datapulse.etl.adapter.wb.dto.WbFinanceRow;
import io.datapulse.etl.adapter.wb.dto.WbOrderItem;
import io.datapulse.etl.adapter.wb.dto.WbPriceGood;
import io.datapulse.etl.adapter.wb.dto.WbReturnItem;
import io.datapulse.etl.adapter.wb.dto.WbSaleItem;
import io.datapulse.etl.adapter.wb.dto.WbStockItem;
import io.datapulse.etl.domain.FinanceEntryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WbNormalizerTest {

  @Mock
  ObjectMapper objectMapper;

  @InjectMocks
  WbNormalizer normalizer;

  @Nested
  @DisplayName("normalizeCatalogCard()")
  class NormalizeCatalogCard {

    @Test
    void should_mapAllFields() {
      var card = new WbCatalogCard(
          12345L, "VENDOR-1", "Brand", "Title", "SubjectName", 1, 1L, "uuid-1",
          "desc",
          List.of(new WbCatalogCard.WbCardSize(1L, "M", "42", List.of("barcode123"))),
          "2024-01-01", "2024-01-02");

      var result = normalizer.normalizeCatalogCard(card);

      assertThat(result.sellerSku()).isEqualTo("VENDOR-1");
      assertThat(result.marketplaceSku()).isEqualTo("12345");
      assertThat(result.marketplaceSkuAlt()).isEqualTo("uuid-1");
      assertThat(result.name()).isEqualTo("Title");
      assertThat(result.brand()).isEqualTo("Brand");
      assertThat(result.category()).isEqualTo("SubjectName");
      assertThat(result.barcode()).isEqualTo("barcode123");
      assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void should_returnNullBarcode_when_noSizes() {
      var card = new WbCatalogCard(
          12345L, "VENDOR-1", "Brand", "Title", "SubjectName", 1, 1L, "uuid-1",
          "desc", List.of(), "2024-01-01", "2024-01-02");

      var result = normalizer.normalizeCatalogCard(card);

      assertThat(result.barcode()).isNull();
    }

    @Test
    void should_returnNullBarcode_when_noSkus() {
      var card = new WbCatalogCard(
          12345L, "VENDOR-1", "Brand", "Title", "SubjectName", 1, 1L, "uuid-1",
          "desc",
          List.of(new WbCatalogCard.WbCardSize(1L, "M", "42", List.of())),
          "2024-01-01", "2024-01-02");

      var result = normalizer.normalizeCatalogCard(card);

      assertThat(result.barcode()).isNull();
    }
  }

  @Nested
  @DisplayName("normalizePrice()")
  class NormalizePrice {

    @Test
    void should_mapPriceFromFirstSize() {
      var good = new WbPriceGood(
          12345L, "VENDOR-1",
          List.of(new WbPriceGood.WbPriceSize(1L, 1000L, 800L, 750L, "M")),
          "RUB", 20, 0, false);

      var result = normalizer.normalizePrice(good);

      assertThat(result.marketplaceSku()).isEqualTo("12345");
      assertThat(result.price()).isEqualByComparingTo(BigDecimal.valueOf(1000));
      assertThat(result.discountPrice()).isEqualByComparingTo(BigDecimal.valueOf(800));
      assertThat(result.discountPct()).isEqualByComparingTo(BigDecimal.valueOf(20));
      assertThat(result.currency()).isEqualTo("RUB");
    }

    @Test
    void should_returnZeroPrices_when_noSizes() {
      var good = new WbPriceGood(12345L, "VENDOR-1", null, "RUB", 20, 0, false);

      var result = normalizer.normalizePrice(good);

      assertThat(result.price()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.discountPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }
  }

  @Nested
  @DisplayName("normalizeStock()")
  class NormalizeStock {

    @Test
    void should_mapAllFields() {
      var item = new WbStockItem(12345L, 1L, 42, "warehouse", "Moscow", 100, 5, 2);

      var result = normalizer.normalizeStock(item);

      assertThat(result.marketplaceSku()).isEqualTo("12345");
      assertThat(result.warehouseId()).isEqualTo("42");
      assertThat(result.available()).isEqualTo(100);
      assertThat(result.reserved()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("normalizeOrder()")
  class NormalizeOrder {

    @Test
    void should_mapOrderFields() {
      var item = new WbOrderItem(
          "srid-1", "gn-1", 12345L, "SA-001", "barcode",
          BigDecimal.valueOf(1500), 10, BigDecimal.valueOf(1350),
          BigDecimal.ZERO, BigDecimal.valueOf(1350),
          false, null, false, false,
          "2024-01-15T10:30:00+03:00", "2024-01-15T10:30:00+03:00",
          "Moscow", "Warehouse-1");

      var result = normalizer.normalizeOrder(item);

      assertThat(result.externalOrderId()).isEqualTo("srid-1");
      assertThat(result.sellerSku()).isEqualTo("SA-001");
      assertThat(result.quantity()).isEqualTo(1);
      assertThat(result.pricePerUnit()).isEqualByComparingTo(BigDecimal.valueOf(1350));
      assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1350));
      assertThat(result.currency()).isEqualTo("RUB");
      assertThat(result.status()).isEqualTo("CREATED");
      assertThat(result.fulfillmentType()).isEqualTo("FBW");
      assertThat(result.region()).isEqualTo("Moscow");
      assertThat(result.orderDate()).isNotNull();
    }

    @Test
    void should_setCancelledStatus_when_isCancelTrue() {
      var item = new WbOrderItem(
          "srid-2", "gn-2", 12345L, "SA-001", "barcode",
          BigDecimal.valueOf(1500), 10, BigDecimal.valueOf(1350),
          BigDecimal.ZERO, BigDecimal.valueOf(1350),
          true, "2024-01-16T00:00:00+03:00", false, false,
          "2024-01-15T10:30:00+03:00", "2024-01-16T00:00:00+03:00",
          "Moscow", "Warehouse-1");

      var result = normalizer.normalizeOrder(item);

      assertThat(result.status()).isEqualTo("CANCELLED");
    }
  }

  @Nested
  @DisplayName("normalizeSale()")
  class NormalizeSale {

    @Test
    void should_mapSaleFields() {
      var item = new WbSaleItem(
          "SALE-001", "srid-1", "gn-1", 12345L, "SA-001", "barcode",
          BigDecimal.valueOf(1500), 10, BigDecimal.valueOf(1000),
          BigDecimal.ZERO, BigDecimal.valueOf(850), BigDecimal.valueOf(1000),
          "2024-01-15T10:30:00+03:00", "2024-01-15T10:30:00+03:00",
          "Moscow", "Warehouse-1");

      var result = normalizer.normalizeSale(item);

      assertThat(result.externalSaleId()).isEqualTo("SALE-001");
      assertThat(result.sellerSku()).isEqualTo("SA-001");
      assertThat(result.quantity()).isEqualTo(1);
      assertThat(result.saleAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
      assertThat(result.commission()).isEqualByComparingTo(BigDecimal.valueOf(150));
      assertThat(result.currency()).isEqualTo("RUB");
      assertThat(result.saleDate()).isNotNull();
    }
  }

  @Nested
  @DisplayName("normalizeReturn()")
  class NormalizeReturn {

    @Test
    void should_mapReturnFields_and_setFbw() {
      var item = new WbReturnItem(
          12345L, "barcode", "srid-1", 100L, 200L, "sticker",
          "Brand", "Subject", "M", "Возврат",
          1, "Брак", "reason",
          "2024-02-10T12:00:00+03:00", null, null, null,
          42L, "Office Address");

      var result = normalizer.normalizeReturn(item);

      assertThat(result.externalReturnId()).isEqualTo("srid-1");
      assertThat(result.quantity()).isEqualTo(1);
      assertThat(result.returnAmount()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.returnReason()).isEqualTo("Брак");
      assertThat(result.currency()).isEqualTo("RUB");
      assertThat(result.status()).isEqualTo("Возврат");
      assertThat(result.fulfillmentType()).isEqualTo("FBW");
      assertThat(result.returnDate()).isNotNull();
    }
  }

  @Nested
  @DisplayName("normalizeFinance()")
  class NormalizeFinance {

    @Test
    void should_mapSaleFinance_with_signConventions() {
      var row = buildFinanceRow(
          "Продажа", BigDecimal.valueOf(1000),
          BigDecimal.valueOf(100), BigDecimal.valueOf(50),
          "2024-01-15T10:30:00+03:00", "2024-01-15");

      var result = normalizer.normalizeFinance(row);

      assertThat(result.entryType()).isEqualTo(FinanceEntryType.WB_SALE);
      assertThat(result.revenueAmount())
          .isEqualByComparingTo(BigDecimal.valueOf(1000));
      assertThat(result.marketplaceCommissionAmount())
          .isEqualByComparingTo(BigDecimal.valueOf(-100));
      assertThat(result.logisticsCostAmount())
          .isEqualByComparingTo(BigDecimal.valueOf(-50));
      assertThat(result.refundAmount())
          .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_mapReturn_to_refundAmount() {
      var row = buildFinanceRow(
          "Возврат", BigDecimal.valueOf(500),
          BigDecimal.ZERO, BigDecimal.ZERO,
          "2024-01-15T10:30:00+03:00", "2024-01-15");

      var result = normalizer.normalizeFinance(row);

      assertThat(result.entryType()).isEqualTo(FinanceEntryType.WB_RETURN);
      assertThat(result.refundAmount())
          .isEqualByComparingTo(BigDecimal.valueOf(-500));
      assertThat(result.revenueAmount())
          .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_fallbackToRrDt_when_saleDtIsNull() {
      var row = buildFinanceRow(
          "Продажа", BigDecimal.valueOf(1000),
          BigDecimal.ZERO, BigDecimal.ZERO,
          null, "2024-01-15");

      var result = normalizer.normalizeFinance(row);

      assertThat(result.entryDate()).isNotNull();
      assertThat(result.entryDate().toLocalDate())
          .isEqualTo(java.time.LocalDate.of(2024, 1, 15));
    }

    @Test
    void should_handleNullFinanceFields() {
      var row = buildFinanceRow(
          "Продажа", null, null, null,
          "2024-01-15T10:30:00+03:00", "2024-01-15");

      var result = normalizer.normalizeFinance(row);

      assertThat(result.revenueAmount()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.marketplaceCommissionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.logisticsCostAmount()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.storageCostAmount()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.penaltiesAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
  }

  private WbFinanceRow buildFinanceRow(String docTypeName, BigDecimal retailPrice,
                                       BigDecimal commission, BigDecimal delivery,
                                       String saleDt, String rrDt) {
    return new WbFinanceRow(
        1L, 100L, rrDt, "2024-01-01", "2024-01-31", "2024-01-15",
        "2024-01-10", saleDt, "srid-1", 12345L, "SA-001", "barcode",
        docTypeName, "supplier_op", 1,
        BigDecimal.ZERO, BigDecimal.ZERO, retailPrice,
        BigDecimal.valueOf(850), BigDecimal.ZERO, BigDecimal.ZERO,
        commission, delivery, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, 42L, "Office Name", null, null, null,
        "order-uid-1", null, "RU", null, null, BigDecimal.ZERO,
        null, null, null, null, null,
        BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, "руб", null,
        null, null, null, null, null, null, null
    );
  }
}
