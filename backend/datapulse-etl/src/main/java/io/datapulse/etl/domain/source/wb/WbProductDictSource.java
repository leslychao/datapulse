package io.datapulse.etl.domain.source.wb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.wb.WbCatalogReadAdapter;
import io.datapulse.etl.adapter.wb.WbNormalizer;
import io.datapulse.etl.adapter.wb.dto.WbCatalogCard;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.domain.normalized.NormalizedCatalogItem;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class WbProductDictSource implements EventSource {

  private final WbCatalogReadAdapter adapter;
  private final WbNormalizer normalizer;
  private final CanonicalEntityMapper mapper;
  private final SubSourceRunner subSourceRunner;
  private final ProductMasterUpsertRepository productMasterRepository;
  private final ProductMasterLookupRepository productMasterLookup;
  private final SellerSkuUpsertRepository sellerSkuRepository;
  private final SkuLookupRepository skuLookup;
  private final MarketplaceOfferUpsertRepository offerRepository;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.WB;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.PRODUCT_DICT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String token = ctx.credentials().get(CredentialKeys.WB_API_TOKEN);
    var captureCtx = CaptureContextFactory.build(ctx, eventType(), "WbCatalogReadAdapter");
    List<CaptureResult> pages = adapter.captureAllPages(captureCtx, token);

    SubSourceResult result = subSourceRunner.processPages(
        "WbCatalogReadAdapter", pages, WbCatalogCard.class,
        batch -> processBatch(batch, ctx));
    return List.of(result);
  }

  private void processBatch(List<WbCatalogCard> batch, IngestContext ctx) {
    List<NormalizedCatalogItem> items = batch.stream()
        .map(normalizer::normalizeCatalogCard)
        .toList();

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
  }
}
