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
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalStockCurrentUpsertRepository;
import io.datapulse.etl.persistence.canonical.SkuLookupRepository;
import io.datapulse.etl.persistence.canonical.WarehouseLookupRepository;
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
    String clientId = ctx.credentials().get("clientId");
    String apiKey = ctx.credentials().get("apiKey");

    var captureCtx = CaptureContextFactory.build(ctx, eventType(), "OzonStocksReadAdapter");
    List<CaptureResult> pages = adapter.captureAllPages(captureCtx, clientId, apiKey);

    Map<String, Long> offerIdMap = skuLookup.findAllOfferIdsByConnection(ctx.connectionId());
    Map<String, Long> warehouseIdMap = warehouseLookup.findAllIdsByConnection(ctx.connectionId());
    OffsetDateTime capturedAt = OffsetDateTime.now();

    SubSourceResult result = subSourceRunner.processPages(
        "OzonStocksReadAdapter", pages, OzonStockItem.class,
        batch -> {
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
}
