package io.datapulse.etl.domain.source.ozon;

import java.util.List;

import io.datapulse.etl.adapter.ozon.OzonFinanceNormalizer;
import io.datapulse.etl.adapter.ozon.OzonFinanceReadAdapter;
import io.datapulse.etl.adapter.ozon.dto.OzonFinanceTransaction;
import io.datapulse.etl.domain.CanonicalFinanceNormalizer;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EtlSubSourceResume;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalFinanceEntryUpsertRepository;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OzonFinanceFactSource implements EventSource {

    private final OzonFinanceReadAdapter adapter;
    private final OzonFinanceNormalizer normalizer;
    private final CanonicalFinanceEntryUpsertRepository repository;
    private final CanonicalFinanceNormalizer financeNormalizer;
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
        String clientId = ctx.credentials().get(CredentialKeys.OZON_CLIENT_ID);
        String apiKey = ctx.credentials().get(CredentialKeys.OZON_API_KEY);

        var captureCtx = CaptureContextFactory.build(ctx, eventType(), "OzonFinanceReadAdapter");
        int financePage =
            EtlSubSourceResume.ozonFinanceStartPage(ctx, eventType(), "OzonFinanceReadAdapter");
        List<CaptureResult> pages = adapter.captureAllPages(
            captureCtx, clientId, apiKey, ctx.ozonFactSince(), ctx.ozonFactTo(), financePage);

        SubSourceResult result = subSourceRunner.processPages(
                "OzonFinanceReadAdapter", pages, OzonFinanceTransaction.class,
                batch -> {
                    var normalized = batch.stream()
                            .map(normalizer::normalizeFinanceTransaction)
                            .toList();
                    repository.batchUpsert(financeNormalizer.normalizeBatch(normalized, ctx));
                });
        return List.of(result);
    }
}
