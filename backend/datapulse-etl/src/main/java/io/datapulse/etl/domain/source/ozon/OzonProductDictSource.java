package io.datapulse.etl.domain.source.ozon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.ozon.OzonAttributesReadAdapter;
import io.datapulse.etl.adapter.ozon.OzonNormalizer;
import io.datapulse.etl.adapter.ozon.OzonProductInfoReadAdapter;
import io.datapulse.etl.adapter.ozon.OzonProductListReadAdapter;
import io.datapulse.etl.adapter.ozon.dto.OzonAttributeResponse;
import io.datapulse.etl.adapter.ozon.dto.OzonProductInfo;
import io.datapulse.etl.adapter.ozon.dto.OzonProductListItem;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EtlSubSourceResume;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.domain.normalized.NormalizedCatalogItem;
import io.datapulse.etl.persistence.canonical.MarketplaceOfferEntity;
import io.datapulse.etl.persistence.canonical.MarketplaceOfferUpsertRepository;
import io.datapulse.etl.persistence.canonical.ProductMasterLookupRepository;
import io.datapulse.etl.persistence.canonical.ProductMasterUpsertRepository;
import io.datapulse.etl.persistence.canonical.ProductMasterUpsertRepository.BrandUpdate;
import io.datapulse.etl.persistence.canonical.SellerSkuEntity;
import io.datapulse.etl.persistence.canonical.SellerSkuUpsertRepository;
import io.datapulse.etl.persistence.canonical.SkuLookupRepository;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ozon PRODUCT_DICT has three sub-sources with internal dependencies:
 * <ol>
 *   <li>Product list (primary, hard) → collects product IDs</li>
 *   <li>Product info (hard dep on #1) → fetches full product data,
 *       creates product_master → seller_sku → marketplace_offer hierarchy</li>
 *   <li>Attributes (soft dep on #1) → brand enrichment, failure is non-fatal</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OzonProductDictSource implements EventSource {

  private final OzonProductListReadAdapter listAdapter;
  private final OzonProductInfoReadAdapter infoAdapter;
  private final OzonAttributesReadAdapter attributesAdapter;
  private final OzonNormalizer normalizer;
  private final CanonicalEntityMapper mapper;
  private final SubSourceRunner subSourceRunner;
  private final ProductMasterUpsertRepository productMasterRepository;
  private final ProductMasterLookupRepository productMasterLookup;
  private final SellerSkuUpsertRepository sellerSkuRepository;
  private final SkuLookupRepository skuLookup;
  private final MarketplaceOfferUpsertRepository offerRepository;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.OZON;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.PRODUCT_DICT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String clientId = ctx.credentials().get(CredentialKeys.OZON_CLIENT_ID);
    String apiKey = ctx.credentials().get(CredentialKeys.OZON_API_KEY);
    List<SubSourceResult> results = new ArrayList<>();

    var listCtx = CaptureContextFactory.build(ctx, eventType(), "OzonProductListReadAdapter");
    String listLastId =
        EtlSubSourceResume.lastIdOrEmpty(ctx, eventType(), "OzonProductListReadAdapter");
    List<CaptureResult> listPages =
        listAdapter.captureAllPages(listCtx, clientId, apiKey, listLastId);

    List<Long> productIds = new ArrayList<>();
    SubSourceResult listResult = subSourceRunner.processPages(
        "OzonProductListReadAdapter", listPages, OzonProductListItem.class,
        batch -> batch.forEach(item -> productIds.add(item.productId())));
    results.add(listResult);

    if (!listResult.isSuccess() || productIds.isEmpty()) {
      return results;
    }

    var infoCtx = CaptureContextFactory.build(ctx, eventType(), "OzonProductInfoReadAdapter");
    int infoBatchStart = EtlSubSourceResume.ozonProductInfoStartBatchIndex(
        ctx, eventType(), "OzonProductInfoReadAdapter");
    List<CaptureResult> infoPages = infoAdapter.captureAllBatches(
        infoCtx, clientId, apiKey, productIds, infoBatchStart);
    SubSourceResult infoResult = subSourceRunner.processPages(
        "OzonProductInfoReadAdapter", infoPages, OzonProductInfo.class,
        batch -> processInfoBatch(batch, ctx));
    results.add(infoResult);

    try {
      var attrCtx = CaptureContextFactory.build(ctx, eventType(), "OzonAttributesReadAdapter");
      String attrLastId =
          EtlSubSourceResume.lastIdOrEmpty(ctx, eventType(), "OzonAttributesReadAdapter");
      List<CaptureResult> attrPages = attributesAdapter.captureAllPages(
          attrCtx, clientId, apiKey, productIds, attrLastId);
      SubSourceResult attrResult = subSourceRunner.processPages(
          "OzonAttributesReadAdapter", attrPages,
          OzonAttributeResponse.OzonAttributeResult.class,
          batch -> {
            List<BrandUpdate> updates = new ArrayList<>();
            for (var attr : batch) {
              String brand = normalizer.extractBrand(attr);
              if (brand != null) {
                updates.add(new BrandUpdate(String.valueOf(attr.id()), brand));
              }
            }
            if (!updates.isEmpty()) {
              productMasterRepository.batchUpdateBrand(ctx.workspaceId(), updates);
            }
          });
      results.add(attrResult);
    } catch (Exception e) {
      log.warn("Ozon attributes enrichment failed (soft dep): connectionId={}, error={}",
          ctx.connectionId(), e.getMessage());
      results.add(SubSourceResult.partial("OzonAttributesReadAdapter",
          null, 0, 0, 0, List.of(e.getMessage())));
    }

    return results;
  }

  private void processInfoBatch(List<OzonProductInfo> batch, IngestContext ctx) {
    List<NormalizedCatalogItem> items = batch.stream()
        .map(normalizer::normalizeProductInfo)
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
