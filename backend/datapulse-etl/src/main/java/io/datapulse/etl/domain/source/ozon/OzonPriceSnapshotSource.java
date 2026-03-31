package io.datapulse.etl.domain.source.ozon;

import java.util.List;

import io.datapulse.etl.adapter.ozon.OzonNormalizer;
import io.datapulse.etl.adapter.ozon.OzonPricesReadAdapter;
import io.datapulse.etl.adapter.ozon.dto.OzonPriceItem;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalPriceCurrentUpsertRepository;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OzonPriceSnapshotSource implements EventSource {

    private final OzonPricesReadAdapter adapter;
    private final OzonNormalizer normalizer;
    private final CanonicalPriceCurrentUpsertRepository repository;
    private final CanonicalEntityMapper mapper;
    private final SubSourceRunner subSourceRunner;

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
        String clientId = ctx.credentials().get("clientId");
        String apiKey = ctx.credentials().get("apiKey");

        var captureCtx = CaptureContextFactory.build(ctx, eventType(), "OzonPricesReadAdapter");
        List<CaptureResult> pages = adapter.captureAllPages(captureCtx, clientId, apiKey);

        SubSourceResult result = subSourceRunner.processPages(
                "OzonPricesReadAdapter", pages, OzonPriceItem.class,
                batch -> repository.batchUpsert(batch.stream()
                        .map(item -> mapper.toPrice(normalizer.normalizePrice(item), ctx))
                        .toList()));
        return List.of(result);
    }
}
