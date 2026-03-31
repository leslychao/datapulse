package io.datapulse.etl.domain.source.ozon;

import java.util.List;

import io.datapulse.etl.adapter.ozon.OzonCategoryTreeReadAdapter;
import io.datapulse.etl.adapter.ozon.OzonNormalizer;
import io.datapulse.etl.adapter.ozon.dto.OzonCategoryTreeResponse;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CategoryUpsertRepository;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OzonCategoryDictSource implements EventSource {

    private final OzonCategoryTreeReadAdapter adapter;
    private final OzonNormalizer normalizer;
    private final CategoryUpsertRepository repository;
    private final CanonicalEntityMapper mapper;
    private final SubSourceRunner subSourceRunner;

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.OZON;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.CATEGORY_DICT;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext ctx) {
        String clientId = ctx.credentials().get("clientId");
        String apiKey = ctx.credentials().get("apiKey");

        var captureCtx = CaptureContextFactory.build(ctx, eventType(), "OzonCategoryTreeReadAdapter");
        CaptureResult page = adapter.capturePage(captureCtx, clientId, apiKey);

        SubSourceResult result = subSourceRunner.processPages(
                "OzonCategoryTreeReadAdapter", List.of(page), OzonCategoryTreeResponse.OzonCategoryNode.class,
                batch -> repository.batchUpsert(
                        normalizer.flattenCategoryTree(batch).stream()
                                .map(cat -> mapper.toCategory(cat, ctx))
                                .toList()));
        return List.of(result);
    }
}
