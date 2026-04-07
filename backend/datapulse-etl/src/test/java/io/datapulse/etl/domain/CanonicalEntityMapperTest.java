package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Map;

import io.datapulse.etl.domain.normalized.NormalizedCatalogItem;
import io.datapulse.etl.domain.normalized.NormalizedCategory;
import io.datapulse.etl.domain.normalized.NormalizedFinanceItem;
import io.datapulse.etl.domain.normalized.NormalizedOrderItem;
import io.datapulse.etl.domain.normalized.NormalizedPriceItem;
import io.datapulse.etl.domain.normalized.NormalizedReturnItem;
import io.datapulse.etl.domain.normalized.NormalizedSaleItem;
import io.datapulse.etl.domain.normalized.NormalizedStockItem;
import io.datapulse.etl.domain.normalized.NormalizedWarehouse;
import io.datapulse.integration.domain.MarketplaceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CanonicalEntityMapperTest {

  private final CanonicalEntityMapper mapper = new CanonicalEntityMapper();

  private IngestContext buildContext() {
    return IngestContextFixtures.any(
        1L, 100L, 1L, MarketplaceType.WB,
        Map.of(), "FULL_SYNC", EnumSet.allOf(EtlEventType.class), Map.of());
  }

  @Nested
  @DisplayName("toWarehouse()")
  class ToWarehouse {

    @Test
    void should_mapAllFields() {
      var norm = new NormalizedWarehouse("WH-001", "Main Warehouse", "FBO", "wb");
      var ctx = buildContext();

      var entity = mapper.toWarehouse(norm, ctx);

      assertThat(entity.getMarketplaceConnectionId()).isEqualTo(100L);
      assertThat(entity.getExternalWarehouseId()).isEqualTo("WH-001");
      assertThat(entity.getName()).isEqualTo("Main Warehouse");
      assertThat(entity.getWarehouseType()).isEqualTo("FBO");
      assertThat(entity.getMarketplaceType()).isEqualTo("wb");
      assertThat(entity.getJobExecutionId()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("toCategory()")
  class ToCategory {

    @Test
    void should_mapAllFields() {
      var norm = new NormalizedCategory("CAT-10", "Electronics", null, "wb");
      var ctx = buildContext();

      var entity = mapper.toCategory(norm, ctx);

      assertThat(entity.getMarketplaceConnectionId()).isEqualTo(100L);
      assertThat(entity.getExternalCategoryId()).isEqualTo("CAT-10");
      assertThat(entity.getName()).isEqualTo("Electronics");
      assertThat(entity.getMarketplaceType()).isEqualTo("wb");
      assertThat(entity.getJobExecutionId()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("toOrder()")
  class ToOrder {

    @Test
    void should_mapAllFields_and_setPlatformName() {
      var orderDate = OffsetDateTime.now();
      var norm = new NormalizedOrderItem(
          "ORD-1", "SKU-1", 3,
          BigDecimal.valueOf(500), BigDecimal.valueOf(1500),
          "RUB", orderDate, "delivered", "FBO", "Moscow");
      var ctx = buildContext();

      var entity = mapper.toOrder(norm, ctx);

      assertThat(entity.getConnectionId()).isEqualTo(100L);
      assertThat(entity.getSourcePlatform()).isEqualTo("wb");
      assertThat(entity.getExternalOrderId()).isEqualTo("ORD-1");
      assertThat(entity.getOrderDate()).isEqualTo(orderDate);
      assertThat(entity.getQuantity()).isEqualTo(3);
      assertThat(entity.getPricePerUnit()).isEqualByComparingTo(BigDecimal.valueOf(500));
      assertThat(entity.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
      assertThat(entity.getCurrency()).isEqualTo("RUB");
      assertThat(entity.getStatus()).isEqualTo("delivered");
      assertThat(entity.getFulfillmentType()).isEqualTo("FBO");
      assertThat(entity.getRegion()).isEqualTo("Moscow");
      assertThat(entity.getJobExecutionId()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("toSale()")
  class ToSale {

    @Test
    void should_mapAllFields() {
      var saleDate = OffsetDateTime.now();
      var norm = new NormalizedSaleItem(
          "SALE-1", "SKU-1", 2,
          BigDecimal.valueOf(1000), BigDecimal.valueOf(150),
          "RUB", saleDate, "FBW");
      var ctx = buildContext();

      var entity = mapper.toSale(norm, ctx);

      assertThat(entity.getConnectionId()).isEqualTo(100L);
      assertThat(entity.getSourcePlatform()).isEqualTo("wb");
      assertThat(entity.getExternalSaleId()).isEqualTo("SALE-1");
      assertThat(entity.getSaleDate()).isEqualTo(saleDate);
      assertThat(entity.getSaleAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
      assertThat(entity.getCommission()).isEqualByComparingTo(BigDecimal.valueOf(150));
      assertThat(entity.getQuantity()).isEqualTo(2);
      assertThat(entity.getFulfillmentType()).isEqualTo("FBW");
      assertThat(entity.getJobExecutionId()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("toReturn()")
  class ToReturn {

    @Test
    void should_mapAllFields_includingFulfillmentType() {
      var returnDate = OffsetDateTime.now();
      var norm = new NormalizedReturnItem(
          "RET-1", "SKU-1", 1,
          BigDecimal.valueOf(500), "defective",
          "RUB", returnDate, "accepted", "FBW");
      var ctx = buildContext();

      var entity = mapper.toReturn(norm, ctx);

      assertThat(entity.getConnectionId()).isEqualTo(100L);
      assertThat(entity.getSourcePlatform()).isEqualTo("wb");
      assertThat(entity.getExternalReturnId()).isEqualTo("RET-1");
      assertThat(entity.getReturnDate()).isEqualTo(returnDate);
      assertThat(entity.getReturnAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
      assertThat(entity.getReturnReason()).isEqualTo("defective");
      assertThat(entity.getQuantity()).isEqualTo(1);
      assertThat(entity.getStatus()).isEqualTo("accepted");
      assertThat(entity.getFulfillmentType()).isEqualTo("FBW");
      assertThat(entity.getJobExecutionId()).isEqualTo(1L);
    }

    @Test
    void should_mapNullFulfillmentType() {
      var returnDate = OffsetDateTime.now();
      var norm = new NormalizedReturnItem(
          "RET-2", "SKU-1", 1,
          BigDecimal.valueOf(300), "damaged",
          "RUB", returnDate, "accepted", null);
      var ctx = buildContext();

      var entity = mapper.toReturn(norm, ctx);

      assertThat(entity.getFulfillmentType()).isNull();
    }
  }

  @Nested
  @DisplayName("toPrice()")
  class ToPrice {

    @Test
    void should_mapAllFields() {
      var norm = new NormalizedPriceItem(
          "SKU-1", BigDecimal.valueOf(1000),
          BigDecimal.valueOf(800), BigDecimal.valueOf(20),
          BigDecimal.valueOf(500), BigDecimal.valueOf(1500), "RUB");
      var ctx = buildContext();

      var entity = mapper.toPrice(norm, ctx);

      assertThat(entity.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(1000));
      assertThat(entity.getDiscountPrice()).isEqualByComparingTo(BigDecimal.valueOf(800));
      assertThat(entity.getDiscountPct()).isEqualByComparingTo(BigDecimal.valueOf(20));
      assertThat(entity.getCurrency()).isEqualTo("RUB");
      assertThat(entity.getJobExecutionId()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("toStock()")
  class ToStock {

    @Test
    void should_mapAllFields() {
      var norm = new NormalizedStockItem("SKU-1", "WH-1", 50, 10);
      var ctx = buildContext();

      var entity = mapper.toStock(norm, ctx);

      assertThat(entity.getAvailable()).isEqualTo(50);
      assertThat(entity.getReserved()).isEqualTo(10);
      assertThat(entity.getJobExecutionId()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("toFinanceEntry()")
  class ToFinanceEntry {

    @Test
    void should_mapAllMeasureColumns() {
      var entryDate = OffsetDateTime.now();
      var norm = new NormalizedFinanceItem(
          "FE-1", FinanceEntryType.SALE_ACCRUAL,
          "POST-1", "ORD-1", "SKU-1", "MSKU-1", "WH-1", "FBW",
          BigDecimal.valueOf(1000),
          BigDecimal.valueOf(100),
          BigDecimal.valueOf(50),
          BigDecimal.valueOf(200),
          BigDecimal.valueOf(30),
          BigDecimal.valueOf(10),
          BigDecimal.valueOf(20),
          BigDecimal.valueOf(15),
          BigDecimal.valueOf(5),
          BigDecimal.valueOf(25),
          BigDecimal.valueOf(40),
          BigDecimal.valueOf(555),
          "RUB", entryDate);
      var ctx = buildContext();

      var entity = mapper.toFinanceEntry(norm, ctx, 42L, 7L, "POSTING");

      assertThat(entity.getConnectionId()).isEqualTo(100L);
      assertThat(entity.getSourcePlatform()).isEqualTo("wb");
      assertThat(entity.getExternalEntryId()).isEqualTo("FE-1");
      assertThat(entity.getEntryType()).isEqualTo("SALE_ACCRUAL");
      assertThat(entity.getPostingId()).isEqualTo("POST-1");
      assertThat(entity.getOrderId()).isEqualTo("ORD-1");
      assertThat(entity.getSellerSkuId()).isEqualTo(42L);
      assertThat(entity.getWarehouseId()).isEqualTo(7L);
      assertThat(entity.getRevenueAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
      assertThat(entity.getMarketplaceCommissionAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
      assertThat(entity.getAcquiringCommissionAmount()).isEqualByComparingTo(BigDecimal.valueOf(50));
      assertThat(entity.getLogisticsCostAmount()).isEqualByComparingTo(BigDecimal.valueOf(200));
      assertThat(entity.getStorageCostAmount()).isEqualByComparingTo(BigDecimal.valueOf(30));
      assertThat(entity.getPenaltiesAmount()).isEqualByComparingTo(BigDecimal.valueOf(10));
      assertThat(entity.getAcceptanceCostAmount()).isEqualByComparingTo(BigDecimal.valueOf(20));
      assertThat(entity.getMarketingCostAmount()).isEqualByComparingTo(BigDecimal.valueOf(15));
      assertThat(entity.getOtherMarketplaceChargesAmount()).isEqualByComparingTo(BigDecimal.valueOf(5));
      assertThat(entity.getCompensationAmount()).isEqualByComparingTo(BigDecimal.valueOf(25));
      assertThat(entity.getRefundAmount()).isEqualByComparingTo(BigDecimal.valueOf(40));
      assertThat(entity.getNetPayout()).isEqualByComparingTo(BigDecimal.valueOf(555));
      assertThat(entity.getCurrency()).isEqualTo("RUB");
      assertThat(entity.getEntryDate()).isEqualTo(entryDate);
      assertThat(entity.getAttributionLevel()).isEqualTo("POSTING");
      assertThat(entity.getFulfillmentType()).isEqualTo("FBW");
      assertThat(entity.getJobExecutionId()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("toProductMaster()")
  class ToProductMaster {

    @Test
    void should_mapAllFields() {
      var norm = new NormalizedCatalogItem(
          "VENDOR-1", "MSKU-1", "ALT-1",
          "Product Name", "BrandX", "Electronics", "4600001234567", "ACTIVE");
      var ctx = buildContext();

      var entity = mapper.toProductMaster(norm, ctx);

      assertThat(entity.getWorkspaceId()).isEqualTo(1L);
      assertThat(entity.getExternalCode()).isEqualTo("VENDOR-1");
      assertThat(entity.getName()).isEqualTo("Product Name");
      assertThat(entity.getBrand()).isEqualTo("BrandX");
      assertThat(entity.getJobExecutionId()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("toSellerSku()")
  class ToSellerSku {

    @Test
    void should_mapAllFields() {
      var norm = new NormalizedCatalogItem(
          "VENDOR-1", "MSKU-1", "ALT-1",
          "Product Name", "BrandX", "Electronics", "4600001234567", "ACTIVE");
      var ctx = buildContext();

      var entity = mapper.toSellerSku(norm, 99L, ctx);

      assertThat(entity.getProductMasterId()).isEqualTo(99L);
      assertThat(entity.getSkuCode()).isEqualTo("VENDOR-1");
      assertThat(entity.getBarcode()).isEqualTo("4600001234567");
      assertThat(entity.getJobExecutionId()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("toMarketplaceOffer()")
  class ToMarketplaceOffer {

    @Test
    void should_setDefaultStatus_when_statusIsNull() {
      var norm = new NormalizedCatalogItem(
          "VENDOR-1", "MSKU-1", "ALT-1",
          "Product Name", "BrandX", "Electronics", "4600001234567", null);
      var ctx = buildContext();

      var entity = mapper.toMarketplaceOffer(norm, 50L, 10L, ctx);

      assertThat(entity.getSellerSkuId()).isEqualTo(50L);
      assertThat(entity.getMarketplaceConnectionId()).isEqualTo(100L);
      assertThat(entity.getMarketplaceSku()).isEqualTo("MSKU-1");
      assertThat(entity.getMarketplaceSkuAlt()).isEqualTo("ALT-1");
      assertThat(entity.getName()).isEqualTo("Product Name");
      assertThat(entity.getCategoryId()).isEqualTo(10L);
      assertThat(entity.getStatus()).isEqualTo("ACTIVE");
      assertThat(entity.getJobExecutionId()).isEqualTo(1L);
    }
  }
}
