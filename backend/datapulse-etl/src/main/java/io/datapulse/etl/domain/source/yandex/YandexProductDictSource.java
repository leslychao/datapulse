package io.datapulse.etl.domain.source.yandex;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.datapulse.etl.adapter.yandex.YandexCatalogReadAdapter;
import io.datapulse.etl.adapter.yandex.YandexNormalizer;
import io.datapulse.etl.adapter.yandex.dto.YandexOfferMapping;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.domain.normalized.NormalizedCatalogItem;
import io.datapulse.etl.domain.normalized.NormalizedPriceItem;
import io.datapulse.etl.persistence.canonical.CanonicalPriceCurrentUpsertRepository;
import io.datapulse.etl.persistence.canonical.MarketplaceOfferEntity;
import io.datapulse.etl.persistence.canonical.MarketplaceOfferUpsertRepository;
import io.datapulse.etl.persistence.canonical.ProductMasterLookupRepository;
import io.datapulse.etl.persistence.canonical.ProductMasterUpsertRepository;
import io.datapulse.etl.persistence.canonical.SellerSkuEntity;
import io.datapulse.etl.persistence.canonical.SellerSkuUpsertRepository;
import io.datapulse.etl.persistence.canonical.SkuLookupRepository;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Yandex PRODUCT_DICT: captures offer-mappings from business-level catalog API.
 * <p>
 * Dual output: catalog data (product_master → seller_sku → marketplace_offer)
 * AND prices (canonical_price_current) are both extracted from the same
 * offer-mappings response, avoiding a separate PRICE_SNAPSHOT API call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YandexProductDictSource implements EventSource {

  private static final String SOURCE_ID = "YandexCatalogReadAdapter";

  private final YandexCatalogReadAdapter adapter;
  private final YandexNormalizer normalizer;
  private final CanonicalEntityMapper mapper;
  private final SubSourceRunner subSourceRunner;
  private final ProductMasterUpsertRepository productMasterRepository;
  private final ProductMasterLookupRepository productMasterLookup;
  private final SellerSkuUpsertRepository sellerSkuRepository;
  private final SkuLookupRepository skuLookup;
  private final MarketplaceOfferUpsertRepository offerRepository;
  private final CanonicalPriceCurrentUpsertRepository priceRepository;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.YANDEX;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.PRODUCT_DICT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String apiKey = ctx.credentials().get(CredentialKeys.YANDEX_API_KEY);
    YandexMetadata meta = YandexMetadata.parse(ctx.connectionMetadata());

    var captureCtx = CaptureContextFactory.build(ctx, eventType(), SOURCE_ID);
    List<CaptureResult> pages = adapter.captureAllPages(
        captureCtx, apiKey, meta.businessId());

    SubSourceResult result = subSourceRunner.processPages(
        SOURCE_ID, pages, YandexOfferMapping.class,
        batch -> processBatch(batch, ctx));
    return List.of(result);
  }

  private void processBatch(List<YandexOfferMapping> batch, IngestContext ctx) {
    List<NormalizedCatalogItem> items = normalizer.normalizeCatalog(batch);

    productMasterRepository.batchUpsert(items.stream()
        .map(item -> mapper.toProductMaster(item, ctx))
        .toList());

    Map<String, Long> pmIds = productMasterLookup.findAllByWorkspace(ctx.workspaceId());

    List<SellerSkuEntity> skuEntities = new ArrayList<>();
    for (NormalizedCatalogItem item : items) {
      Long pmId = pmIds.get(item.sellerSku());
      if (pmId == null) {
        log.warn("ProductMaster lookup miss: sellerSku={}", item.sellerSku());
        continue;
      }
      skuEntities.add(mapper.toSellerSku(item, pmId, ctx));
    }
    if (!skuEntities.isEmpty()) {
      sellerSkuRepository.batchUpsert(skuEntities);
    }

    Map<String, Long> skuIds = skuLookup.findAllByWorkspace(ctx.workspaceId());

    List<MarketplaceOfferEntity> offerEntities = new ArrayList<>();
    for (NormalizedCatalogItem item : items) {
      if (item.marketplaceSku() == null) {
        continue;
      }
      Long skuId = skuIds.get(item.sellerSku());
      if (skuId == null) {
        log.warn("SellerSku lookup miss: sellerSku={}", item.sellerSku());
        continue;
      }
      offerEntities.add(mapper.toMarketplaceOffer(item, skuId, null, ctx));
    }
    if (!offerEntities.isEmpty()) {
      offerRepository.batchUpsert(offerEntities);
    }

    upsertPricesFromCatalog(batch, ctx);
  }

  /**
   * Dual output: prices are embedded in offer-mappings (offer.basicPrice).
   * Extract and upsert them here to avoid a separate PRICE_SNAPSHOT call.
   */
  private void upsertPricesFromCatalog(List<YandexOfferMapping> batch, IngestContext ctx) {
    Map<String, Long> offerIdMap =
        skuLookup.findAllOfferIdsByConnection(ctx.connectionId());
    OffsetDateTime capturedAt = OffsetDateTime.now();

    List<NormalizedPriceItem> priceItems = normalizer.normalizePrices(batch);
    var priceEntities = priceItems.stream()
        .map(norm -> {
          Long offerId = offerIdMap.get(norm.marketplaceSku());
          if (offerId == null) {
            return null;
          }
          var entity = mapper.toPrice(norm, ctx);
          entity.setMarketplaceOfferId(offerId);
          entity.setCapturedAt(capturedAt);
          return entity;
        })
        .filter(Objects::nonNull)
        .toList();

    if (!priceEntities.isEmpty()) {
      priceRepository.batchUpsert(priceEntities);
      log.debug("Yandex dual-output prices upserted: connectionId={}, count={}",
          ctx.connectionId(), priceEntities.size());
    }
  }
}
