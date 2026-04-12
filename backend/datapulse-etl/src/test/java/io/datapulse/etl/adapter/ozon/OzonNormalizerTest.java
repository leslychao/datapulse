package io.datapulse.etl.adapter.ozon;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.etl.adapter.ozon.dto.OzonAttributeResponse;
import io.datapulse.etl.adapter.ozon.dto.OzonCategoryTreeResponse;
import io.datapulse.etl.adapter.ozon.dto.OzonFboPosting;
import io.datapulse.etl.adapter.ozon.dto.OzonPriceItem;
import io.datapulse.etl.adapter.ozon.dto.OzonProductInfo;
import io.datapulse.etl.adapter.ozon.dto.OzonReturnItem;
import io.datapulse.etl.adapter.ozon.dto.OzonStockItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OzonNormalizerTest {

  @Mock
  ObjectMapper objectMapper;

  @InjectMocks
  OzonNormalizer normalizer;

  @Nested
  @DisplayName("normalizeProductInfo()")
  class NormalizeProductInfo {

    @Test
    void should_mapAllFields() {
      var info = new OzonProductInfo(
          99001L, "OFFER-1", "Product Name", "barcode-single",
          List.of("barcode-list-1"), 100L, 200L,
          "2024-01-01T00:00:00Z", false, false,
          new OzonProductInfo.OzonProductVisibility(true),
          null, null);

      var result = normalizer.normalizeProductInfo(info);

      assertThat(result.sellerSku()).isEqualTo("OFFER-1");
      assertThat(result.marketplaceSku()).isEqualTo("99001");
      assertThat(result.name()).isEqualTo("Product Name");
      assertThat(result.barcode()).isEqualTo("barcode-list-1");
      assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void should_returnArchived_when_isArchived() {
      var info = new OzonProductInfo(
          99001L, "OFFER-1", "Product", "barcode",
          null, 100L, 200L, "2024-01-01T00:00:00Z",
          true, false,
          new OzonProductInfo.OzonProductVisibility(true),
          null, null);

      var result = normalizer.normalizeProductInfo(info);

      assertThat(result.status()).isEqualTo("ARCHIVED");
    }

    @Test
    void should_returnInactive_when_notVisible() {
      var info = new OzonProductInfo(
          99001L, "OFFER-1", "Product", "barcode",
          null, 100L, 200L, "2024-01-01T00:00:00Z",
          false, false,
          new OzonProductInfo.OzonProductVisibility(false),
          null, null);

      var result = normalizer.normalizeProductInfo(info);

      assertThat(result.status()).isEqualTo("INACTIVE");
    }

    @Test
    void should_returnActive_when_visibilityNull() {
      var info = new OzonProductInfo(
          99001L, "OFFER-1", "Product", "barcode",
          null, 100L, 200L, "2024-01-01T00:00:00Z",
          false, false,
          null,
          null, null);

      var result = normalizer.normalizeProductInfo(info);

      assertThat(result.status()).isEqualTo("ACTIVE");
    }
  }

  @Nested
  @DisplayName("normalizePrice()")
  class NormalizePrice {

    @Test
    void should_parsePriceFromString() {
      var item = new OzonPriceItem(
          99001L, "OFFER-1",
          new OzonPriceItem.OzonPriceObject(
              "1299.00", "1500.00", "999.00",
              "1200.00", "1100.00", "RUB"),
          null, null);

      var result = normalizer.normalizePrice(item);

      assertThat(result.marketplaceSku()).isEqualTo("99001");
      assertThat(result.price()).isEqualByComparingTo(new BigDecimal("1299.00"));
      assertThat(result.discountPrice()).isEqualByComparingTo(new BigDecimal("1100.00"));
      assertThat(result.minPrice()).isEqualByComparingTo(new BigDecimal("999.00"));
      assertThat(result.currency()).isEqualTo("RUB");
    }
  }

  @Nested
  @DisplayName("normalizeStocks()")
  class NormalizeStocks {

    @Test
    void should_mapStockEntries() {
      var item = new OzonStockItem(
          99001L, "OFFER-1",
          List.of(
              new OzonStockItem.OzonStockEntry(50, 10, "fbo", List.of(777L), "WH-1"),
              new OzonStockItem.OzonStockEntry(30, 5, "fbs", List.of(888L), "WH-2")));

      var result = normalizer.normalizeStocks(item);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).marketplaceSku()).isEqualTo("99001");
      assertThat(result.get(0).warehouseId()).isEqualTo("777");
      assertThat(result.get(0).available()).isEqualTo(50);
      assertThat(result.get(0).reserved()).isEqualTo(10);
      assertThat(result.get(1).warehouseId()).isEqualTo("888");
    }

    @Test
    void should_returnEmptyList_when_stocksNull() {
      var item = new OzonStockItem(99001L, "OFFER-1", null);

      var result = normalizer.normalizeStocks(item);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("normalizeFboPosting()")
  class NormalizeFboPosting {

    @Test
    void should_mapOrderFields() {
      var product = new OzonFboPosting.OzonPostingProduct(
          55555L, "OFFER-1", "Product", 2, "500.00", "RUB");
      var posting = new OzonFboPosting(
          "87621408-0010-1", 1001L, "ORD-001", "delivered",
          "2024-01-15T10:00:00+03:00", "2024-01-15T10:00:00+03:00",
          List.of(product),
          new OzonFboPosting.OzonAnalyticsData("Moscow", "Moscow", "FBO", "WH-Main", 777L),
          null);

      var result = normalizer.normalizeFboPosting(posting, product);

      assertThat(result.externalOrderId()).isEqualTo("87621408-0010-1-55555");
      assertThat(result.sellerSku()).isEqualTo("OFFER-1");
      assertThat(result.quantity()).isEqualTo(2);
      assertThat(result.pricePerUnit()).isEqualByComparingTo(new BigDecimal("500.00"));
      assertThat(result.totalAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
      assertThat(result.currency()).isEqualTo("RUB");
      assertThat(result.status()).isEqualTo("delivered");
      assertThat(result.fulfillmentType()).isEqualTo("FBO");
      assertThat(result.region()).isEqualTo("Moscow");
      assertThat(result.orderDate()).isNotNull();
    }

    @Test
    void should_produceUniqueOrderIds_for_multiItemPosting() {
      var product1 = new OzonFboPosting.OzonPostingProduct(
          11111L, "OFFER-A", "Product A", 1, "300.00", "RUB");
      var product2 = new OzonFboPosting.OzonPostingProduct(
          22222L, "OFFER-B", "Product B", 2, "500.00", "RUB");
      var posting = new OzonFboPosting(
          "87621408-0010-1", 1001L, "ORD-001", "delivered",
          "2024-01-15T10:00:00+03:00", null,
          List.of(product1, product2),
          null, null);

      var result1 = normalizer.normalizeFboPosting(posting, product1);
      var result2 = normalizer.normalizeFboPosting(posting, product2);

      assertThat(result1.externalOrderId()).isEqualTo("87621408-0010-1-11111");
      assertThat(result2.externalOrderId()).isEqualTo("87621408-0010-1-22222");
      assertThat(result1.externalOrderId()).isNotEqualTo(result2.externalOrderId());
    }
  }

  @Nested
  @DisplayName("normalizeReturn()")
  class NormalizeReturn {

    @Test
    void should_mapReturnFields() {
      var item = new OzonReturnItem(
          5001L, 6001L, 1001L, "ORD-001", "87621408-0010-1",
          "returned_to_seller", "Defective product",
          "2024-02-10T12:00:00+03:00", "2024-02-09T10:00:00+03:00",
          new OzonReturnItem.OzonReturnProduct(
              "OFFER-1", "Product", 55555L, 1,
              new OzonReturnItem.OzonReturnPrice("750.00", "RUB")),
          false, 0L,
          null,
          null);

      var result = normalizer.normalizeReturn(item);

      assertThat(result.externalReturnId()).isEqualTo("5001");
      assertThat(result.sellerSku()).isEqualTo("OFFER-1");
      assertThat(result.quantity()).isEqualTo(1);
      assertThat(result.returnAmount()).isEqualByComparingTo(new BigDecimal("750.00"));
      assertThat(result.returnReason()).isEqualTo("Defective product");
      assertThat(result.currency()).isEqualTo("RUB");
      assertThat(result.status()).isEqualTo("returned_to_seller");
      assertThat(result.fulfillmentType()).isNull();
      assertThat(result.returnDate()).isNotNull();
    }

    @Test
    void should_useLogisticReturnDate_when_topLevelReturnDateNull() {
      var logistic = new OzonReturnItem.OzonReturnLogistic(
          "2024-07-13T01:47:09.440Z",
          "2024-07-14T22:06:27.340Z",
          null,
          null,
          null);
      var item = new OzonReturnItem(
          5001L, 6001L, 1001L, "ORD-001", null,
          "returned_to_seller", "Defective product",
          null, null,
          new OzonReturnItem.OzonReturnProduct(
              "OFFER-1", "Product", 55555L, 1,
              new OzonReturnItem.OzonReturnPrice("750.00", "RUB")),
          false, 0L,
          logistic,
          null);

      var result = normalizer.normalizeReturn(item);

      assertThat(result.returnDate().toInstant().toString()).startsWith("2024-07-13T01:47:09");
    }
  }

  @Nested
  @DisplayName("flattenCategoryTree()")
  class FlattenCategoryTree {

    @Test
    void should_flattenNestedCategories() {
      var child1 = new OzonCategoryTreeResponse.OzonCategoryNode(
          201L, "Child-1", false, null);
      var child2 = new OzonCategoryTreeResponse.OzonCategoryNode(
          202L, "Child-2", false, null);
      var parent = new OzonCategoryTreeResponse.OzonCategoryNode(
          100L, "Parent", false, List.of(child1, child2));

      var result = normalizer.flattenCategoryTree(List.of(parent));

      assertThat(result).hasSize(3);
      assertThat(result.get(0).externalCategoryId()).isEqualTo("100");
      assertThat(result.get(0).name()).isEqualTo("Parent");
      assertThat(result.get(0).parentExternalCategoryId()).isNull();
      assertThat(result.get(1).externalCategoryId()).isEqualTo("201");
      assertThat(result.get(1).parentExternalCategoryId()).isEqualTo("100");
      assertThat(result.get(2).externalCategoryId()).isEqualTo("202");
      assertThat(result.get(2).parentExternalCategoryId()).isEqualTo("100");
    }
  }

  @Nested
  @DisplayName("extractBrand()")
  class ExtractBrand {

    @Test
    void should_extractBrand_from_attributeId85() {
      var attrResult = new OzonAttributeResponse.OzonAttributeResult(
          99001L, "OFFER-1",
          List.of(
              new OzonAttributeResponse.OzonAttribute(85L,
                  List.of(new OzonAttributeResponse.OzonAttributeValue("BrandName", 0L))),
              new OzonAttributeResponse.OzonAttribute(100L,
                  List.of(new OzonAttributeResponse.OzonAttributeValue("Other", 0L)))));

      var result = normalizer.extractBrand(attrResult);

      assertThat(result).isEqualTo("BrandName");
    }

    @Test
    void should_returnNull_when_noBrandAttribute() {
      var attrResult = new OzonAttributeResponse.OzonAttributeResult(
          99001L, "OFFER-1",
          List.of(
              new OzonAttributeResponse.OzonAttribute(100L,
                  List.of(new OzonAttributeResponse.OzonAttributeValue("Other", 0L)))));

      var result = normalizer.extractBrand(attrResult);

      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("isDeliveredPosting()")
  class IsDeliveredPosting {

    @Test
    void should_returnTrue_for_delivered() {
      assertThat(normalizer.isDeliveredPosting("delivered")).isTrue();
      assertThat(normalizer.isDeliveredPosting("client_arbitration")).isTrue();
      assertThat(normalizer.isDeliveredPosting("arbitration")).isTrue();
    }

    @Test
    void should_returnFalse_for_awaiting() {
      assertThat(normalizer.isDeliveredPosting("awaiting_deliver")).isFalse();
      assertThat(normalizer.isDeliveredPosting("awaiting_packaging")).isFalse();
      assertThat(normalizer.isDeliveredPosting(null)).isFalse();
    }
  }
}
