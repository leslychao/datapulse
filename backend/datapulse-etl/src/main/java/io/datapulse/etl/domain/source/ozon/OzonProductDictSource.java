package io.datapulse.etl.domain.source.ozon;

import java.util.ArrayList;
import java.util.List;

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
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.ProductMasterUpsertRepository;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ozon PRODUCT_DICT has three sub-sources with internal dependencies:
 * <ol>
 *   <li>Product list (primary, hard) → collects product IDs</li>
 *   <li>Product info (hard dep on #1) → fetches full product data</li>
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
    private final ProductMasterUpsertRepository repository;
    private final CanonicalEntityMapper mapper;
    private final SubSourceRunner subSourceRunner;

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
        String clientId = ctx.credentials().get("clientId");
        String apiKey = ctx.credentials().get("apiKey");
        List<SubSourceResult> results = new ArrayList<>();

        var listCtx = CaptureContextFactory.build(ctx, eventType(), "OzonProductListReadAdapter");
        List<CaptureResult> listPages = listAdapter.captureAllPages(listCtx, clientId, apiKey);

        List<Long> productIds = new ArrayList<>();
        SubSourceResult listResult = subSourceRunner.processPages(
                "OzonProductListReadAdapter", listPages, OzonProductListItem.class,
                batch -> batch.forEach(item -> productIds.add(item.productId())));
        results.add(listResult);

        if (!listResult.isSuccess() || productIds.isEmpty()) {
            return results;
        }

        var infoCtx = CaptureContextFactory.build(ctx, eventType(), "OzonProductInfoReadAdapter");
        List<CaptureResult> infoPages = infoAdapter.captureAllBatches(infoCtx, clientId, apiKey, productIds);
        SubSourceResult infoResult = subSourceRunner.processPages(
                "OzonProductInfoReadAdapter", infoPages, OzonProductInfo.class,
                batch -> repository.batchUpsert(batch.stream()
                        .map(info -> mapper.toProductMaster(normalizer.normalizeProductInfo(info), ctx))
                        .toList()));
        results.add(infoResult);

        try {
            var attrCtx = CaptureContextFactory.build(ctx, eventType(), "OzonAttributesReadAdapter");
            List<CaptureResult> attrPages = attributesAdapter.captureAllPages(attrCtx, clientId, apiKey, productIds);
            SubSourceResult attrResult = subSourceRunner.processPages(
                    "OzonAttributesReadAdapter", attrPages, OzonAttributeResponse.OzonAttributeResult.class,
                    batch -> batch.forEach(attr -> {
                        String brand = normalizer.extractBrand(attr);
                        if (brand != null) {
                            log.debug("Brand enrichment: productId={}", attr);
                        }
                    }));
            results.add(attrResult);
        } catch (Exception e) {
            log.warn("Ozon attributes enrichment failed (soft dep): connectionId={}, error={}",
                    ctx.connectionId(), e.getMessage());
            results.add(SubSourceResult.partial("OzonAttributesReadAdapter",
                    null, 0, 0, 0, List.of(e.getMessage())));
        }

        return results;
    }
}
