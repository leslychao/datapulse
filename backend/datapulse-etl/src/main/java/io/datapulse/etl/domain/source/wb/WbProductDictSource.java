package io.datapulse.etl.domain.source.wb;

import java.util.List;

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
import io.datapulse.etl.persistence.canonical.ProductMasterUpsertRepository;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WbProductDictSource implements EventSource {

    private final WbCatalogReadAdapter adapter;
    private final WbNormalizer normalizer;
    private final ProductMasterUpsertRepository repository;
    private final CanonicalEntityMapper mapper;
    private final SubSourceRunner subSourceRunner;

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
        String token = ctx.credentials().get("apiToken");
        var captureCtx = CaptureContextFactory.build(ctx, eventType(), "WbCatalogReadAdapter");
        List<CaptureResult> pages = adapter.captureAllPages(captureCtx, token);

        SubSourceResult result = subSourceRunner.processPages(
                "WbCatalogReadAdapter", pages, WbCatalogCard.class,
                batch -> repository.batchUpsert(batch.stream()
                        .map(card -> mapper.toProductMaster(normalizer.normalizeCatalogCard(card), ctx))
                        .toList()));
        return List.of(result);
    }
}
