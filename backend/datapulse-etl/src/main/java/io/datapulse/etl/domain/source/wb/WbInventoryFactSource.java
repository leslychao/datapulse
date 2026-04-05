package io.datapulse.etl.domain.source.wb;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.datapulse.etl.adapter.wb.WbNormalizer;
import io.datapulse.etl.adapter.wb.WbStocksReadAdapter;
import io.datapulse.etl.adapter.wb.dto.WbStockItem;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class WbInventoryFactSource implements EventSource {

  private final WbStocksReadAdapter adapter;
  private final WbNormalizer normalizer;
  private final CanonicalStockCurrentUpsertRepository repository;
  private final CanonicalEntityMapper mapper;
  private final SubSourceRunner subSourceRunner;
  private final SkuLookupRepository skuLookup;
  private final WarehouseLookupRepository warehouseLookup;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.WB;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.INVENTORY_FACT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String token = ctx.credentials().get(CredentialKeys.WB_API_TOKEN);
    var captureCtx = CaptureContextFactory.build(ctx, eventType(), "WbStocksReadAdapter");
    List<CaptureResult> pages = adapter.captureAllPages(captureCtx, token);

    Map<String, Long> offerIdMap = skuLookup.findAllOfferIdsByConnection(ctx.connectionId());
    Map<String, Long> warehouseIdMap = warehouseLookup.findAllIdsByConnection(ctx.connectionId());
    OffsetDateTime capturedAt = OffsetDateTime.now();

    SubSourceResult result = subSourceRunner.processPages(
        "WbStocksReadAdapter", pages, WbStockItem.class,
        batch -> {
          var entities = batch.stream()
              .map(item -> {
                var norm = normalizer.normalizeStock(item);
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
