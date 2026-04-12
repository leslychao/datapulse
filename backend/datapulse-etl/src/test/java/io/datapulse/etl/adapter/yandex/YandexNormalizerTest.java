package io.datapulse.etl.adapter.yandex;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import io.datapulse.etl.adapter.yandex.dto.YandexBasicPrice;
import io.datapulse.etl.adapter.yandex.dto.YandexDelivery;
import io.datapulse.etl.adapter.yandex.dto.YandexMapping;
import io.datapulse.etl.adapter.yandex.dto.YandexOffer;
import io.datapulse.etl.adapter.yandex.dto.YandexOfferMapping;
import io.datapulse.etl.adapter.yandex.dto.YandexOrder;
import io.datapulse.etl.adapter.yandex.dto.YandexOrderItem;
import io.datapulse.etl.adapter.yandex.dto.YandexOrderPrice;
import io.datapulse.etl.adapter.yandex.dto.YandexRealizationReportRow;
import io.datapulse.etl.adapter.yandex.dto.YandexRegion;
import io.datapulse.etl.adapter.yandex.dto.YandexReturn;
import io.datapulse.etl.adapter.yandex.dto.YandexReturnItem;
import io.datapulse.etl.adapter.yandex.dto.YandexReturnReason;
import io.datapulse.etl.adapter.yandex.dto.YandexServicesReportRow;
import io.datapulse.etl.adapter.yandex.dto.YandexStockEntry;
import io.datapulse.etl.adapter.yandex.dto.YandexStockOffer;
import io.datapulse.etl.adapter.yandex.dto.YandexStockWarehouse;
import io.datapulse.etl.domain.FinanceEntryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class YandexNormalizerTest {

  private final YandexNormalizer normalizer = new YandexNormalizer();

  @Nested
  @DisplayName("normalizeCatalog()")
  class NormalizeCatalog {

    @Test
    void should_mapAllFields_when_fullOfferMapping() {
      var mapping = buildFullOfferMapping();

      var result = normalizer.normalizeCatalog(List.of(mapping));

      assertThat(result).hasSize(1);
      var item = result.get(0);
      assertThat(item.sellerSku()).isEqualTo("TEST-SKU-001");
      assertThat(item.marketplaceSku()).isEqualTo("200100500");
      assertThat(item.name()).isEqualTo("Ударная дрель Makita HP1630, 710 Вт");
      assertThat(item.brand()).isEqualTo("Makita");
      assertThat(item.category()).isEqualTo("Дрели");
      assertThat(item.barcode()).isEqualTo("4607159324843");
      assertThat(item.status()).isEqualTo("ACTIVE");
    }

    @Test
    void should_handleNullMapping_when_noMarketSku() {
      var offer = buildOffer("SKU-NO-MAP", "Product", "Brand",
          List.of("111"), "HAS_CARD_CAN_UPDATE", false);
      var mapping = new YandexOfferMapping(offer, null);

      var result = normalizer.normalizeCatalog(List.of(mapping));

      assertThat(result).hasSize(1);
      assertThat(result.get(0).marketplaceSku()).isNull();
      assertThat(result.get(0).category()).isNull();
    }

    @Test
    void should_deriveArchived_when_archivedTrue() {
      var offer = buildOffer("SKU-ARCH", "Archived item", "Brand",
          List.of(), "HAS_CARD_CAN_UPDATE", true);
      var mapping = new YandexOfferMapping(offer, buildMapping());

      var result = normalizer.normalizeCatalog(List.of(mapping));

      assertThat(result.get(0).status()).isEqualTo("ARCHIVED");
    }

    @Test
    void should_normalizeVendorAsBrand() {
      var offer = buildOffer("SKU-V", "Item", "Bosch",
          List.of("999"), "HAS_CARD_CAN_UPDATE", false);
      var mapping = new YandexOfferMapping(offer, buildMapping());

      var result = normalizer.normalizeCatalog(List.of(mapping));

      assertThat(result.get(0).brand()).isEqualTo("Bosch");
    }

    @Test
    void should_deriveBlocked_when_noCardStatus() {
      var offer = buildOffer("SKU-NC", "Item", "Brand",
          List.of(), "NO_CARD_NEED_CONTENT", false);
      var mapping = new YandexOfferMapping(offer, buildMapping());

      var result = normalizer.normalizeCatalog(List.of(mapping));

      assertThat(result.get(0).status()).isEqualTo("BLOCKED");
    }

    @Test
    void should_returnEmptyList_when_nullInput() {
      var result = normalizer.normalizeCatalog(null);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("normalizePrices()")
  class NormalizePrices {

    @Test
    void should_mapBasicPrice_and_normalizeCurrency() {
      var mapping = buildFullOfferMapping();

      var result = normalizer.normalizePrices(List.of(mapping));

      assertThat(result).hasSize(1);
      var price = result.get(0);
      assertThat(price.marketplaceSku()).isEqualTo("TEST-SKU-001");
      assertThat(price.price()).isEqualByComparingTo(BigDecimal.valueOf(5990));
      assertThat(price.maxPrice()).isEqualByComparingTo(BigDecimal.valueOf(3200));
      assertThat(price.currency()).isEqualTo("RUB");
    }
  }

  @Nested
  @DisplayName("normalizeStocks()")
  class NormalizeStocks {

    @Test
    void should_flattenWarehouseOfferHierarchy() {
      var warehouses = List.of(
          new YandexStockWarehouse(70001L, List.of(
              buildStockOffer("SKU-1", 50, 42, 8),
              buildStockOffer("SKU-2", 120, 115, 5)
          )),
          new YandexStockWarehouse(70002L, List.of(
              buildStockOffer("SKU-1", 15, 15, 0)
          ))
      );

      var result = normalizer.normalizeStocks(warehouses);

      assertThat(result).hasSize(3);
    }

    @Test
    void should_mapStockTypes_correctly() {
      var warehouses = List.of(
          new YandexStockWarehouse(70001L, List.of(
              buildStockOffer("SKU-1", 50, 42, 8)
          ))
      );

      var result = normalizer.normalizeStocks(warehouses);

      assertThat(result).hasSize(1);
      var stock = result.get(0);
      assertThat(stock.marketplaceSku()).isEqualTo("SKU-1");
      assertThat(stock.warehouseId()).isEqualTo("70001");
      assertThat(stock.available()).isEqualTo(42);
      assertThat(stock.reserved()).isEqualTo(8);
    }

    @Test
    void should_handleEmptyStocks() {
      var warehouses = List.of(
          new YandexStockWarehouse(70001L, List.of(
              new YandexStockOffer("SKU-EMPTY", List.of(), null, null)
          ))
      );

      var result = normalizer.normalizeStocks(warehouses);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).available()).isZero();
      assertThat(result.get(0).reserved()).isZero();
    }

    @Test
    void should_deriveAvailable_from_fitMinusReserve_when_noAvailableType() {
      var offer = new YandexStockOffer("SKU-CALC", List.of(
          new YandexStockEntry("FIT", 100),
          new YandexStockEntry("FREEZE", 30)
      ), null, null);
      var warehouses = List.of(new YandexStockWarehouse(1L, List.of(offer)));

      var result = normalizer.normalizeStocks(warehouses);

      assertThat(result.get(0).available()).isEqualTo(70);
      assertThat(result.get(0).reserved()).isEqualTo(30);
    }
  }

  @Nested
  @DisplayName("normalizeOrders()")
  class NormalizeOrders {

    @Test
    void should_extractBuyerPrice_from_pricesArray() {
      var order = buildOrder(300001L, "DELIVERED", "2026-03-25", "FBY",
          List.of(buildOrderItem("TEST-SKU-001", 1, BigDecimal.valueOf(5990),
              BigDecimal.valueOf(5990))),
          "Москва");

      var result = normalizer.normalizeOrders(List.of(order));

      assertThat(result).hasSize(1);
      var item = result.get(0);
      assertThat(item.pricePerUnit()).isEqualByComparingTo(BigDecimal.valueOf(5990));
      assertThat(item.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(5990));
      assertThat(item.currency()).isEqualTo("RUB");
    }

    @Test
    void should_mapProgramType_to_fulfillmentType() {
      var fbyOrder = buildOrder(1L, "DELIVERED", "2026-03-25", "FBY",
          List.of(buildOrderItem("SKU", 1, BigDecimal.TEN, BigDecimal.TEN)), null);
      var fbsOrder = buildOrder(2L, "PROCESSING", "2026-04-10", "FBS",
          List.of(buildOrderItem("SKU", 1, BigDecimal.TEN, BigDecimal.TEN)), null);

      var fbyResult = normalizer.normalizeOrders(List.of(fbyOrder));
      var fbsResult = normalizer.normalizeOrders(List.of(fbsOrder));

      assertThat(fbyResult.get(0).fulfillmentType()).isEqualTo("FBY");
      assertThat(fbsResult.get(0).fulfillmentType()).isEqualTo("FBS");
    }

    @Test
    void should_handleMultipleItems() {
      var order = buildOrder(300001L, "DELIVERED", "2026-03-25", "FBY",
          List.of(
              buildOrderItem("SKU-1", 1, BigDecimal.valueOf(100), BigDecimal.valueOf(100)),
              buildOrderItem("SKU-2", 3, BigDecimal.valueOf(50), BigDecimal.valueOf(150))
          ), "Москва");

      var result = normalizer.normalizeOrders(List.of(order));

      assertThat(result).hasSize(2);
      assertThat(result.get(0).sellerSku()).isEqualTo("SKU-1");
      assertThat(result.get(1).sellerSku()).isEqualTo("SKU-2");
      assertThat(result.get(1).quantity()).isEqualTo(3);
    }

    @Test
    void should_mapRegion_from_delivery() {
      var order = buildOrder(1L, "DELIVERED", "2026-03-25", "FBY",
          List.of(buildOrderItem("SKU", 1, BigDecimal.TEN, BigDecimal.TEN)),
          "Санкт-Петербург");

      var result = normalizer.normalizeOrders(List.of(order));

      assertThat(result.get(0).region()).isEqualTo("Санкт-Петербург");
    }
  }

  @Nested
  @DisplayName("normalizeReturns()")
  class NormalizeReturns {

    @Test
    void should_mapReturnFields() {
      var ret = buildReturn(400001L, 300001L, "2026-04-02",
          "RECEIVED_ON_MARKETPLACE",
          List.of(buildReturnItem("TEST-SKU-001", 1, "BAD_QUALITY")));

      var result = normalizer.normalizeReturns(List.of(ret));

      assertThat(result).hasSize(1);
      var item = result.get(0);
      assertThat(item.externalReturnId()).isEqualTo("400001");
      assertThat(item.quantity()).isEqualTo(1);
      assertThat(item.returnAmount()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(item.returnReason()).isEqualTo("BAD_QUALITY");
      assertThat(item.currency()).isEqualTo("RUB");
      assertThat(item.status()).isEqualTo("RECEIVED_ON_MARKETPLACE");
      assertThat(item.returnDate()).isNotNull();
    }

    @Test
    void should_mapShopSku_as_sellerSku() {
      var ret = buildReturn(400002L, 300003L, "2026-04-09",
          "WAITING_FOR_RETURN",
          List.of(buildReturnItem("MY-SELLER-SKU", 2, "DO_NOT_FIT")));

      var result = normalizer.normalizeReturns(List.of(ret));

      assertThat(result.get(0).sellerSku()).isEqualTo("MY-SELLER-SKU");
    }

    @Test
    void should_returnEmptyList_when_nullInput() {
      var result = normalizer.normalizeReturns(null);

      assertThat(result).isEmpty();
    }
  }

  // --- Builders ---

  private YandexOfferMapping buildFullOfferMapping() {
    var offer = new YandexOffer(
        "TEST-SKU-001",
        "Ударная дрель Makita HP1630, 710 Вт",
        "Makita",
        List.of("4607159324843"),
        "VNDR-0005A",
        "Ударная дрель мощностью 710 Вт",
        null,
        new YandexBasicPrice(BigDecimal.valueOf(5990), "RUR"),
        new YandexBasicPrice(BigDecimal.valueOf(3200), "RUR"),
        "HAS_CARD_CAN_NOT_UPDATE",
        null,
        null,
        false,
        null
    );
    var mapping = new YandexMapping(
        200100500L, "Дрель Makita HP1630", "Makita HP1630",
        91597, "Дрели");
    return new YandexOfferMapping(offer, mapping);
  }

  private YandexOffer buildOffer(String offerId, String name, String vendor,
      List<String> barcodes, String cardStatus, boolean archived) {
    return new YandexOffer(
        offerId, name, vendor, barcodes, null, null, null,
        new YandexBasicPrice(BigDecimal.valueOf(1000), "RUR"),
        null, cardStatus, null, null, archived, null);
  }

  private YandexMapping buildMapping() {
    return new YandexMapping(100L, "name", "model", 1, "Category");
  }

  private YandexStockOffer buildStockOffer(String offerId, int fit,
      int available, int freeze) {
    return new YandexStockOffer(offerId, List.of(
        new YandexStockEntry("FIT", fit),
        new YandexStockEntry("AVAILABLE", available),
        new YandexStockEntry("FREEZE", freeze)
    ), null, null);
  }

  private YandexOrder buildOrder(long id, String status, String creationDate,
      String programType, List<YandexOrderItem> items, String regionName) {
    YandexDelivery delivery = regionName != null
        ? new YandexDelivery("YANDEX_MARKET", "DELIVERY", List.of(),
        new YandexRegion(213, regionName))
        : null;
    return new YandexOrder(id, status, null, creationDate, null, "PREPAID",
        programType, 10001L, items, delivery);
  }

  private YandexOrderItem buildOrderItem(String offerId, int count,
      BigDecimal costPerItem, BigDecimal total) {
    return new YandexOrderItem(offerId, offerId, null, count,
        List.of(new YandexOrderPrice("BUYER", costPerItem, total)));
  }

  private YandexReturn buildReturn(long id, long orderId, String creationDate,
      String status, List<YandexReturnItem> items) {
    return new YandexReturn(id, orderId, creationDate, null, status, "RETURN",
        items);
  }

  private YandexReturnItem buildReturnItem(String shopSku, int count,
      String reasonType) {
    return new YandexReturnItem(null, shopSku, count, "REFUND_MONEY",
        new YandexReturnReason(reasonType, "Описание"));
  }

  @Nested
  @DisplayName("normalizeServiceCharge()")
  class NormalizeServiceCharge {

    @Test
    void should_mapCommission_to_marketplaceCommission() {
      var row = new YandexServicesReportRow(
          1001L, "SKU-1", "Product", "2024-01-15T10:00:00",
          "FBY", 100L, "1234567890",
          BigDecimal.valueOf(1000), 1,
          "Размещение заказа", "2024-01-15T10:00:00",
          "2024-01-31", BigDecimal.valueOf(5), null,
          BigDecimal.valueOf(50),
          null, null, null, null, null);

      var result = normalizer.normalizeServiceCharge(row);

      assertThat(result.entryType()).isEqualTo(FinanceEntryType.YANDEX_COMMISSION);
      assertThat(result.marketplaceCommissionAmount())
          .isEqualByComparingTo(BigDecimal.valueOf(-50));
      assertThat(result.revenueAmount()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.orderId()).isEqualTo("1001");
      assertThat(result.currency()).isEqualTo("RUB");
    }

    @Test
    void should_mapStorage_to_storageCost() {
      var row = new YandexServicesReportRow(
          null, "SKU-2", "Product", null,
          "FBY", 100L, null,
          null, null,
          "Хранение на складе", "2024-02-01T00:00:00",
          null, null, null,
          BigDecimal.valueOf(120),
          null, null, null, null, null);

      var result = normalizer.normalizeServiceCharge(row);

      assertThat(result.entryType()).isEqualTo(FinanceEntryType.YANDEX_STORAGE);
      assertThat(result.storageCostAmount())
          .isEqualByComparingTo(BigDecimal.valueOf(-120));
    }

    @Test
    void should_mapUnknownService_to_OTHER() {
      var row = new YandexServicesReportRow(
          1003L, "SKU-3", "Product", null,
          "FBS", 100L, null,
          null, null,
          "Новая неизвестная услуга", null,
          null, null, null,
          BigDecimal.valueOf(10),
          null, null, null, null, null);

      var result = normalizer.normalizeServiceCharge(row);

      assertThat(result.entryType()).isEqualTo(FinanceEntryType.OTHER);
    }

    @Test
    void should_handleNullTotalAmount() {
      var row = new YandexServicesReportRow(
          1004L, "SKU-4", "Product", null,
          "FBY", 100L, null,
          null, null,
          "Размещение заказа", null,
          null, null, null,
          null,
          null, null, null, null, null);

      var result = normalizer.normalizeServiceCharge(row);

      assertThat(result.netPayout()).isEqualByComparingTo(BigDecimal.ZERO);
    }
  }

  @Nested
  @DisplayName("normalizeRealization()")
  class NormalizeRealization {

    @Test
    void should_calculateRevenue_from_priceAndCount() {
      var row = new YandexRealizationReportRow(
          2001L, "EXT-2001", "VENDOR-1", "SKU-1", "Product Name",
          "2024-01-10", "2024-01-12", "2024-01-14",
          3, BigDecimal.valueOf(500),
          "20%", "FBY", 100L);

      var result = normalizer.normalizeRealization(row);

      assertThat(result.entryType()).isEqualTo(FinanceEntryType.YANDEX_SALE);
      assertThat(result.revenueAmount())
          .isEqualByComparingTo(BigDecimal.valueOf(1500));
      assertThat(result.orderId()).isEqualTo("2001");
      assertThat(result.sellerSku()).isEqualTo("VENDOR-1");
      assertThat(result.marketplaceSku()).isEqualTo("SKU-1");
      assertThat(result.entryDate()).isNotNull();
    }

    @Test
    void should_handleNullCount_as_zero() {
      var row = new YandexRealizationReportRow(
          2002L, null, null, "SKU-2", null,
          null, null, null,
          null, BigDecimal.valueOf(1000),
          null, null, null);

      var result = normalizer.normalizeRealization(row);

      assertThat(result.revenueAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_handleNullPrice_as_zero() {
      var row = new YandexRealizationReportRow(
          2003L, null, null, "SKU-3", null,
          null, null, null,
          5, null,
          null, null, null);

      var result = normalizer.normalizeRealization(row);

      assertThat(result.revenueAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
  }
}
