package io.datapulse.etl.domain.source.ozon;

import java.time.OffsetDateTime;
import java.util.List;

import io.datapulse.etl.adapter.ozon.OzonFinanceReadAdapter;
import io.datapulse.etl.adapter.ozon.OzonNormalizer;
import io.datapulse.etl.adapter.ozon.dto.OzonFinanceTransaction;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalFinanceEntryUpsertRepository;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OzonFinanceFactSource implements EventSource {

    private final OzonFinanceReadAdapter adapter;
    private final OzonNormalizer normalizer;
    private final CanonicalFinanceEntryUpsertRepository repository;
    private final CanonicalEntityMapper mapper;
    private final SubSourceRunner subSourceRunner;

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.OZON;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.FACT_FINANCE;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext ctx) {
        String clientId = ctx.credentials().get("clientId");
        String apiKey = ctx.credentials().get("apiKey");
        OffsetDateTime since = OffsetDateTime.now().minusDays(7);

        var captureCtx = CaptureContextFactory.build(ctx, eventType(), "OzonFinanceReadAdapter");
        List<CaptureResult> pages = adapter.captureAllPages(captureCtx, clientId, apiKey, since, OffsetDateTime.now());

        SubSourceResult result = subSourceRunner.processPages(
                "OzonFinanceReadAdapter", pages, OzonFinanceTransaction.class,
                batch -> repository.batchUpsert(batch.stream()
                        .map(tx -> mapper.toFinanceEntry(normalizer.normalizeFinanceTransaction(tx), ctx))
                        .toList()));
        return List.of(result);
    }
}
