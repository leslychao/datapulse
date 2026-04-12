package io.datapulse.etl.domain.source.yandex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.datapulse.etl.adapter.yandex.YandexCatalogReadAdapter;
import io.datapulse.etl.adapter.yandex.YandexNormalizer;
import io.datapulse.etl.adapter.yandex.dto.YandexOfferMapping;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventResultStatus;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalPriceCurrentUpsertRepository;
import io.datapulse.etl.persistence.canonical.MarketplaceOfferUpsertRepository;
import io.datapulse.etl.persistence.canonical.ProductMasterLookupRepository;
import io.datapulse.etl.persistence.canonical.ProductMasterUpsertRepository;
import io.datapulse.etl.persistence.canonical.SellerSkuUpsertRepository;
import io.datapulse.etl.persistence.canonical.SkuLookupRepository;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YandexProductDictSourceTest {

  @Mock YandexCatalogReadAdapter adapter;
  @Mock YandexNormalizer normalizer;
  @Mock CanonicalEntityMapper mapper;
  @Mock SubSourceRunner subSourceRunner;
  @Mock ProductMasterUpsertRepository productMasterRepository;
  @Mock ProductMasterLookupRepository productMasterLookup;
  @Mock SellerSkuUpsertRepository sellerSkuRepository;
  @Mock SkuLookupRepository skuLookup;
  @Mock MarketplaceOfferUpsertRepository offerRepository;
  @Mock CanonicalPriceCurrentUpsertRepository priceRepository;

  @InjectMocks YandexProductDictSource source;

  private static final String METADATA_JSON = """
      {"businessId": 67890, "campaigns": [{"campaignId": 10001, "placementType": "FBS"}]}""";

  @Test
  @DisplayName("marketplace() should return YANDEX")
  void should_returnYandexMarketplace() {
    assertThat(source.marketplace()).isEqualTo(MarketplaceType.YANDEX);
  }

  @Test
  @DisplayName("eventType() should return PRODUCT_DICT")
  void should_returnProductDictEventType() {
    assertThat(source.eventType()).isEqualTo(EtlEventType.PRODUCT_DICT);
  }

  @Nested
  @DisplayName("execute()")
  class Execute {

    @Test
    @DisplayName("should call adapter and return success when pages captured")
    void should_captureAndNormalize_when_offerMappingsAvailable() {
      var ctx = buildIngestContext();
      var captureResult = new CaptureResult(1L, "s3://test/page1.json",
          "sha256", 1024L);

      when(adapter.captureAllPages(any(CaptureContext.class), anyString(), eq(67890L)))
          .thenReturn(List.of(captureResult));

      var expectedResult = SubSourceResult.success(
          "YandexCatalogReadAdapter", 1, 2);
      when(subSourceRunner.processPages(
          eq("YandexCatalogReadAdapter"),
          eq(List.of(captureResult)),
          eq(YandexOfferMapping.class),
          any()))
          .thenReturn(expectedResult);

      List<SubSourceResult> results = source.execute(ctx);

      assertThat(results).hasSize(1);
      assertThat(results.get(0).isSuccess()).isTrue();
      assertThat(results.get(0).recordsProcessed()).isEqualTo(2);

      verify(adapter).captureAllPages(
          any(CaptureContext.class), eq("test-api-key"), eq(67890L));
    }

    @Test
    @DisplayName("should propagate failure from SubSourceRunner")
    void should_propagateFailure_when_adapterFails() {
      var ctx = buildIngestContext();

      when(adapter.captureAllPages(any(), anyString(), anyLong()))
          .thenReturn(List.of());

      var failedResult = SubSourceResult.failed(
          "YandexCatalogReadAdapter", "Connection refused");
      when(subSourceRunner.processPages(anyString(), any(), any(), any()))
          .thenReturn(failedResult);

      List<SubSourceResult> results = source.execute(ctx);

      assertThat(results).hasSize(1);
      assertThat(results.get(0).status()).isEqualTo(EventResultStatus.FAILED);
    }
  }

  private IngestContext buildIngestContext() {
    return new IngestContext(
        100L,
        1L,
        10L,
        MarketplaceType.YANDEX,
        Map.of(CredentialKeys.YANDEX_API_KEY, "test-api-key"),
        "FULL_SYNC",
        Set.of(EtlEventType.PRODUCT_DICT),
        null,
        null,
        null,
        null,
        null,
        METADATA_JSON
    );
  }
}
