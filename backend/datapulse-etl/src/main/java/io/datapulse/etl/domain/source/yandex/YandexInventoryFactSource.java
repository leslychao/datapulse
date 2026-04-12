package io.datapulse.etl.domain.source.yandex;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.datapulse.etl.adapter.yandex.YandexNormalizer;
import io.datapulse.etl.adapter.yandex.YandexStocksReadAdapter;
import io.datapulse.etl.adapter.yandex.dto.YandexStockWarehouse;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalStockCurrentUpsertRepository;
import io.datapulse.etl.persistence.canonical.SkuLookupRepository;
import io.datapulse.etl.persistence.canonical.WarehouseLookupRepository;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Yandex stocks are campaign-level: the adapter fans out requests
 * across all campaigns discovered in connection metadata.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YandexInventoryFactSource implements EventSource {

  private static final String SOURCE_ID = "YandexStocksReadAdapter";

  private final YandexStocksReadAdapter adapter;
  private final YandexNormalizer normalizer;
  private final CanonicalEntityMapper mapper;
  private final SubSourceRunner subSourceRunner;
  private final CanonicalStockCurrentUpsertRepository repository;
  private final SkuLookupRepository skuLookup;
  private final WarehouseLookupRepository warehouseLookup;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.YANDEX;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.INVENTORY_FACT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String apiKey = ctx.credentials().get(CredentialKeys.YANDEX_API_KEY);
    YandexMetadata meta = YandexMetadata.parse(ctx.connectionMetadata());

    List<Long> campaignIds = meta.campaignIds();
    if (campaignIds.isEmpty()) {
      log.warn("No Yandex campaigns in metadata, skipping INVENTORY_FACT: connectionId={}",
          ctx.connectionId());
      return List.of(SubSourceResult.success(SOURCE_ID, 0, 0));
    }

    var captureCtx = CaptureContextFactory.build(ctx, eventType(), SOURCE_ID);
    List<CaptureResult> pages = adapter.captureAllPages(captureCtx, apiKey, campaignIds);

    Map<String, Long> offerIdMap = skuLookup.findAllOfferIdsByConnection(ctx.connectionId());
    Map<String, Long> warehouseIdMap = warehouseLookup.findAllIdsByConnection(ctx.connectionId());
    OffsetDateTime capturedAt = OffsetDateTime.now();

    SubSourceResult result = subSourceRunner.processPages(
        SOURCE_ID, pages, YandexStockWarehouse.class,
        batch -> processStockBatch(batch, ctx, offerIdMap, warehouseIdMap, capturedAt));
    return List.of(result);
  }

  private void processStockBatch(
      List<YandexStockWarehouse> batch,
      IngestContext ctx,
      Map<String, Long> offerIdMap,
      Map<String, Long> warehouseIdMap,
      OffsetDateTime capturedAt) {

    var normalized = normalizer.normalizeStocks(batch);

    var entities = normalized.stream()
        .map(norm -> {
          Long offerId = offerIdMap.get(norm.marketplaceSku());
          if (offerId == null) {
            log.debug("Skipping stock: no marketplace_offer for sku={}",
                norm.marketplaceSku());
            return null;
          }
          Long warehouseId = warehouseIdMap.get(norm.warehouseId());
          if (warehouseId == null) {
            log.debug("Skipping stock: no warehouse for externalId={}",
                norm.warehouseId());
            return null;
          }
          var entity = mapper.toStock(norm, ctx);
          entity.setMarketplaceOfferId(offerId);
          entity.setWarehouseId(warehouseId);
          entity.setCapturedAt(capturedAt);
          return entity;
        })
        .filter(Objects::nonNull)
        .toList();

    if (!entities.isEmpty()) {
      repository.batchUpsert(entities);
    }
  }
}
