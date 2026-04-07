package io.datapulse.etl.domain.source.ozon;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.datapulse.etl.adapter.ozon.OzonNormalizer;
import io.datapulse.etl.adapter.ozon.OzonStocksReadAdapter;
import io.datapulse.etl.adapter.ozon.dto.OzonStockItem;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EtlSubSourceResume;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalStockCurrentUpsertRepository;
import io.datapulse.etl.persistence.canonical.SkuLookupRepository;
import io.datapulse.etl.persistence.canonical.WarehouseLookupRepository;
import io.datapulse.etl.persistence.canonical.WarehouseUpsertRepository;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OzonInventoryFactSource implements EventSource {

  private final OzonStocksReadAdapter adapter;
  private final OzonNormalizer normalizer;
  private final CanonicalStockCurrentUpsertRepository repository;
  private final WarehouseUpsertRepository warehouseUpsertRepository;
  private final CanonicalEntityMapper mapper;
  private final SubSourceRunner subSourceRunner;
  private final SkuLookupRepository skuLookup;
  private final WarehouseLookupRepository warehouseLookup;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.OZON;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.INVENTORY_FACT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String clientId = ctx.credentials().get(CredentialKeys.OZON_CLIENT_ID);
    String apiKey = ctx.credentials().get(CredentialKeys.OZON_API_KEY);

    var captureCtx = CaptureContextFactory.build(ctx, eventType(), "OzonStocksReadAdapter");
    String stocksLastId =
        EtlSubSourceResume.lastIdOrEmpty(ctx, eventType(), "OzonStocksReadAdapter");
    List<CaptureResult> pages =
        adapter.captureAllPages(captureCtx, clientId, apiKey, stocksLastId);

    Map<String, Long> offerIdMap = skuLookup.findAllOfferIdsByConnection(ctx.connectionId());
    var warehouseIdMap = new java.util.HashMap<>(
        warehouseLookup.findAllIdsByConnection(ctx.connectionId()));
    OffsetDateTime capturedAt = OffsetDateTime.now();

    SubSourceResult result = subSourceRunner.processPages(
        "OzonStocksReadAdapter", pages, OzonStockItem.class,
        batch -> {
          discoverWarehouses(batch, warehouseIdMap, ctx);

          var entities = batch.stream()
              .flatMap(item -> normalizer.normalizeStocks(item).stream())
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
        });
    return List.of(result);
  }

  /**
   * Ozon has no dedicated warehouse API. Warehouses are auto-discovered
   * from stock entries and upserted before stock processing.
   */
  private void discoverWarehouses(List<OzonStockItem> batch,
                                  Map<String, Long> warehouseIdMap,
                                  IngestContext ctx) {
    var warehouses = normalizer.extractWarehouses(batch);
    var newWarehouses = warehouses.stream()
        .filter(w -> !warehouseIdMap.containsKey(w.externalWarehouseId()))
        .map(w -> mapper.toWarehouse(w, ctx))
        .toList();

    if (newWarehouses.isEmpty()) {
      return;
    }

    warehouseUpsertRepository.batchUpsert(newWarehouses);
    log.info("Auto-discovered Ozon warehouses: count={}, connectionId={}",
        newWarehouses.size(), ctx.connectionId());

    var refreshed = warehouseLookup.findAllIdsByConnection(ctx.connectionId());
    warehouseIdMap.putAll(refreshed);
  }
}
