package io.datapulse.etl.domain.source.ozon;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.datapulse.etl.adapter.ozon.OzonNormalizer;
import io.datapulse.etl.adapter.ozon.OzonPricesReadAdapter;
import io.datapulse.etl.adapter.ozon.dto.OzonPriceItem;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EtlSubSourceResume;
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
public class OzonPriceSnapshotSource implements EventSource {

  private final OzonPricesReadAdapter adapter;
  private final OzonNormalizer normalizer;
  private final CanonicalPriceCurrentUpsertRepository repository;
  private final CanonicalEntityMapper mapper;
  private final SubSourceRunner subSourceRunner;
  private final SkuLookupRepository skuLookup;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.OZON;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.PRICE_SNAPSHOT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String clientId = ctx.credentials().get(CredentialKeys.OZON_CLIENT_ID);
    String apiKey = ctx.credentials().get(CredentialKeys.OZON_API_KEY);

    var captureCtx = CaptureContextFactory.build(ctx, eventType(), "OzonPricesReadAdapter");
    String pricesLastId =
        EtlSubSourceResume.lastIdOrEmpty(ctx, eventType(), "OzonPricesReadAdapter");
    List<CaptureResult> pages =
        adapter.captureAllPages(captureCtx, clientId, apiKey, pricesLastId);

    Map<String, Long> offerIdMap = skuLookup.findAllOfferIdsByConnection(ctx.connectionId());
    OffsetDateTime capturedAt = OffsetDateTime.now();

    SubSourceResult result = subSourceRunner.processPages(
        "OzonPricesReadAdapter", pages, OzonPriceItem.class,
        batch -> {
          var entities = batch.stream()
              .map(item -> {
                var norm = normalizer.normalizePrice(item);
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
