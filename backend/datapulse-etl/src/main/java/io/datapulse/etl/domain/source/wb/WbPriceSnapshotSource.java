package io.datapulse.etl.domain.source.wb;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.datapulse.etl.adapter.wb.WbNormalizer;
import io.datapulse.etl.adapter.wb.WbPricesReadAdapter;
import io.datapulse.etl.adapter.wb.dto.WbPriceGood;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalPriceCurrentUpsertRepository;
import io.datapulse.etl.persistence.canonical.SkuLookupRepository;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WbPriceSnapshotSource implements EventSource {

  private final WbPricesReadAdapter adapter;
  private final WbNormalizer normalizer;
  private final CanonicalPriceCurrentUpsertRepository repository;
  private final CanonicalEntityMapper mapper;
  private final SubSourceRunner subSourceRunner;
  private final SkuLookupRepository skuLookup;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.WB;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.PRICE_SNAPSHOT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String token = ctx.credentials().get(CredentialKeys.WB_API_TOKEN);
    var captureCtx = CaptureContextFactory.build(ctx, eventType(), "WbPricesReadAdapter");
    List<CaptureResult> pages = adapter.captureAllPages(captureCtx, token);

    Map<String, Long> offerIdMap = skuLookup.findAllOfferIdsByConnection(ctx.connectionId());
    OffsetDateTime capturedAt = OffsetDateTime.now();

    SubSourceResult result = subSourceRunner.processPages(
        "WbPricesReadAdapter", pages, WbPriceGood.class,
        batch -> {
          var entities = batch.stream()
              .map(good -> {
                var norm = normalizer.normalizePrice(good);
                Long offerId = offerIdMap.get(norm.marketplaceSku());
                if (offerId == null) {
                  log.debug("Skipping price: no marketplace_offer for sku={}",
                      norm.marketplaceSku());
                  return null;
                }
                var entity = mapper.toPrice(norm, ctx);
                entity.setMarketplaceOfferId(offerId);
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
