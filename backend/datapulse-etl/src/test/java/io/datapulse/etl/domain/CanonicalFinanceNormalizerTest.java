package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.datapulse.etl.domain.normalized.NormalizedFinanceItem;
import io.datapulse.etl.persistence.canonical.CanonicalFinanceEntryEntity;
import io.datapulse.etl.persistence.canonical.SkuLookupRepository;
import io.datapulse.etl.persistence.canonical.WarehouseLookupRepository;
import io.datapulse.integration.domain.MarketplaceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CanonicalFinanceNormalizerTest {

  @Mock
  private SkuLookupRepository skuLookup;

  @Mock
  private WarehouseLookupRepository warehouseLookup;

  @Mock
  private CanonicalEntityMapper mapper;

  @InjectMocks
  private CanonicalFinanceNormalizer normalizer;

  private IngestContext buildContext() {
    return new IngestContext(1L, 100L, 1L, MarketplaceType.WB,
        Map.of(), "FULL_SYNC", EnumSet.allOf(EtlEventType.class), Map.of());
  }

  private NormalizedFinanceItem buildItem(String marketplaceSku, String sellerSku,
                                          String postingId, String orderId,
                                          String warehouseExternalId) {
    return new NormalizedFinanceItem(
        "entry-1", FinanceEntryType.SALE_ACCRUAL, postingId, orderId,
        sellerSku, marketplaceSku, warehouseExternalId,
        BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(100),
        "RUB", OffsetDateTime.now());
  }

  @Nested
  @DisplayName("normalizeBatch()")
  class NormalizeBatch {

    @Test
    void should_normalizeAllItems() {
      var ctx = buildContext();
      var items = List.of(
          buildItem("M1", "S1", "P1", null, null),
          buildItem("M2", "S2", "P2", null, null),
          buildItem("M3", "S3", "P3", null, null));
      when(skuLookup.findByMarketplaceSku(eq(100L), any()))
          .thenReturn(Optional.of(1L));
      when(mapper.toFinanceEntry(any(), any(), any(), any(), any()))
          .thenReturn(new CanonicalFinanceEntryEntity());

      normalizer.normalizeBatch(items, ctx);

      verify(mapper, org.mockito.Mockito.times(3))
          .toFinanceEntry(any(), eq(ctx), any(), any(), any());
    }

    @Test
    void should_useCacheForRepeatedSkuLookups() {
      var ctx = buildContext();
      var items = List.of(
          buildItem("SAME-SKU", "S1", "P1", null, null),
          buildItem("SAME-SKU", "S2", "P2", null, null));
      when(skuLookup.findByMarketplaceSku(100L, "SAME-SKU"))
          .thenReturn(Optional.of(1L));
      when(mapper.toFinanceEntry(any(), any(), any(), any(), any()))
          .thenReturn(new CanonicalFinanceEntryEntity());

      normalizer.normalizeBatch(items, ctx);

      verify(skuLookup, org.mockito.Mockito.times(1))
          .findByMarketplaceSku(100L, "SAME-SKU");
    }
  }

  @Nested
  @DisplayName("computeAttribution()")
  class ComputeAttribution {

    @Test
    void should_returnPosting_when_postingIdPresent() {
      var ctx = buildContext();
      var item = buildItem("M1", "S1", "POST-1", null, null);
      when(skuLookup.findByMarketplaceSku(100L, "M1"))
          .thenReturn(Optional.of(1L));
      when(mapper.toFinanceEntry(any(), any(), any(), any(), any()))
          .thenReturn(new CanonicalFinanceEntryEntity());

      normalizer.normalize(item, ctx);

      verify(mapper).toFinanceEntry(eq(item), eq(ctx), eq(1L), any(), eq("POSTING"));
    }

    @Test
    void should_returnPosting_when_orderIdPresent() {
      var ctx = buildContext();
      var item = buildItem("M1", "S1", null, "ORD-1", null);
      when(skuLookup.findByMarketplaceSku(100L, "M1"))
          .thenReturn(Optional.of(1L));
      when(mapper.toFinanceEntry(any(), any(), any(), any(), any()))
          .thenReturn(new CanonicalFinanceEntryEntity());

      normalizer.normalize(item, ctx);

      verify(mapper).toFinanceEntry(eq(item), eq(ctx), eq(1L), any(), eq("POSTING"));
    }

    @Test
    void should_returnProduct_when_skuResolved() {
      var ctx = buildContext();
      var item = buildItem("M1", "S1", null, null, null);
      when(skuLookup.findByMarketplaceSku(100L, "M1"))
          .thenReturn(Optional.of(1L));
      when(mapper.toFinanceEntry(any(), any(), any(), any(), any()))
          .thenReturn(new CanonicalFinanceEntryEntity());

      normalizer.normalize(item, ctx);

      verify(mapper).toFinanceEntry(eq(item), eq(ctx), eq(1L), any(), eq("PRODUCT"));
    }

    @Test
    void should_returnAccount_when_noSkuResolved() {
      var ctx = buildContext();
      var item = buildItem(null, null, null, null, null);
      when(mapper.toFinanceEntry(any(), any(), any(), any(), any()))
          .thenReturn(new CanonicalFinanceEntryEntity());

      normalizer.normalize(item, ctx);

      verify(mapper).toFinanceEntry(eq(item), eq(ctx), eq(null), any(), eq("ACCOUNT"));
    }
  }

  @Nested
  @DisplayName("SKU resolution")
  class SkuResolution {

    @Test
    void should_resolveByMarketplaceSku_first() {
      var ctx = buildContext();
      var item = buildItem("M1", "S1", null, null, null);
      when(skuLookup.findByMarketplaceSku(100L, "M1"))
          .thenReturn(Optional.of(42L));
      when(mapper.toFinanceEntry(any(), any(), any(), any(), any()))
          .thenReturn(new CanonicalFinanceEntryEntity());

      normalizer.normalize(item, ctx);

      verify(mapper).toFinanceEntry(eq(item), eq(ctx), eq(42L), any(), any());
    }

    @Test
    void should_fallbackToVendorCode_when_marketplaceSkuNotFound() {
      var ctx = buildContext();
      var item = buildItem("M1", "S1", null, null, null);
      when(skuLookup.findByMarketplaceSku(100L, "M1"))
          .thenReturn(Optional.empty());
      when(skuLookup.findByVendorCode(1L, "S1"))
          .thenReturn(Optional.of(99L));
      when(mapper.toFinanceEntry(any(), any(), any(), any(), any()))
          .thenReturn(new CanonicalFinanceEntryEntity());

      normalizer.normalize(item, ctx);

      verify(mapper).toFinanceEntry(eq(item), eq(ctx), eq(99L), any(), any());
    }

    @Test
    void should_returnNull_when_bothLookupsFail() {
      var ctx = buildContext();
      var item = buildItem("M1", "S1", null, null, null);
      when(skuLookup.findByMarketplaceSku(100L, "M1"))
          .thenReturn(Optional.empty());
      when(skuLookup.findByVendorCode(1L, "S1"))
          .thenReturn(Optional.empty());
      when(mapper.toFinanceEntry(any(), any(), any(), any(), any()))
          .thenReturn(new CanonicalFinanceEntryEntity());

      normalizer.normalize(item, ctx);

      verify(mapper).toFinanceEntry(eq(item), eq(ctx), eq(null), any(), eq("ACCOUNT"));
    }
  }

  @Nested
  @DisplayName("Warehouse resolution")
  class WarehouseResolution {

    @Test
    void should_resolveWarehouse_when_externalIdPresent() {
      var ctx = buildContext();
      var item = buildItem("M1", "S1", "P1", null, "WH-EXT-1");
      when(skuLookup.findByMarketplaceSku(100L, "M1"))
          .thenReturn(Optional.of(1L));
      when(warehouseLookup.findByExternalId(100L, "WH-EXT-1"))
          .thenReturn(Optional.of(77L));
      when(mapper.toFinanceEntry(any(), any(), any(), any(), any()))
          .thenReturn(new CanonicalFinanceEntryEntity());

      normalizer.normalize(item, ctx);

      verify(mapper).toFinanceEntry(eq(item), eq(ctx), eq(1L), eq(77L), any());
    }

    @Test
    void should_returnNull_when_noExternalId() {
      var ctx = buildContext();
      var item = buildItem("M1", "S1", "P1", null, null);
      when(skuLookup.findByMarketplaceSku(100L, "M1"))
          .thenReturn(Optional.of(1L));
      when(mapper.toFinanceEntry(any(), any(), any(), any(), any()))
          .thenReturn(new CanonicalFinanceEntryEntity());

      normalizer.normalize(item, ctx);

      verify(mapper).toFinanceEntry(eq(item), eq(ctx), eq(1L), eq(null), any());
    }
  }
}
