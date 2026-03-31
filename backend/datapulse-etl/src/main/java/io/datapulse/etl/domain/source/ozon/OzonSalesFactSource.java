package io.datapulse.etl.domain.source.ozon;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import io.datapulse.etl.adapter.ozon.OzonFboOrdersReadAdapter;
import io.datapulse.etl.adapter.ozon.OzonFbsOrdersReadAdapter;
import io.datapulse.etl.adapter.ozon.OzonNormalizer;
import io.datapulse.etl.adapter.ozon.OzonReturnsReadAdapter;
import io.datapulse.etl.adapter.ozon.dto.OzonFboPosting;
import io.datapulse.etl.adapter.ozon.dto.OzonFbsPosting;
import io.datapulse.etl.adapter.ozon.dto.OzonReturnItem;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalOrderUpsertRepository;
import io.datapulse.etl.persistence.canonical.CanonicalReturnUpsertRepository;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OzonSalesFactSource implements EventSource {

    private final OzonFboOrdersReadAdapter fboAdapter;
    private final OzonFbsOrdersReadAdapter fbsAdapter;
    private final OzonReturnsReadAdapter returnsAdapter;
    private final OzonNormalizer normalizer;
    private final CanonicalOrderUpsertRepository orderRepo;
    private final CanonicalReturnUpsertRepository returnRepo;
    private final CanonicalEntityMapper mapper;
    private final SubSourceRunner subSourceRunner;

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.OZON;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.SALES_FACT;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext ctx) {
        String clientId = ctx.credentials().get("clientId");
        String apiKey = ctx.credentials().get("apiKey");
        OffsetDateTime since = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to = OffsetDateTime.now();
        List<SubSourceResult> results = new ArrayList<>();

        var fboCtx = CaptureContextFactory.build(ctx, eventType(), "OzonFboOrdersReadAdapter");
        List<CaptureResult> fboPages = fboAdapter.captureAllPages(fboCtx, clientId, apiKey, since, to);
        results.add(subSourceRunner.processPages(
                "OzonFboOrdersReadAdapter", fboPages, OzonFboPosting.class,
                batch -> batch.forEach(posting ->
                        posting.products().forEach(product ->
                                orderRepo.batchUpsert(List.of(
                                        mapper.toOrder(normalizer.normalizeFboPosting(posting, product), ctx)))))));

        var fbsCtx = CaptureContextFactory.build(ctx, eventType(), "OzonFbsOrdersReadAdapter");
        List<CaptureResult> fbsPages = fbsAdapter.captureAllPages(fbsCtx, clientId, apiKey, since, to);
        results.add(subSourceRunner.processPages(
                "OzonFbsOrdersReadAdapter", fbsPages, OzonFbsPosting.class,
                batch -> batch.forEach(posting ->
                        posting.products().forEach(product ->
                                orderRepo.batchUpsert(List.of(
                                        mapper.toOrder(normalizer.normalizeFbsPosting(posting, product), ctx)))))));

        var returnsCtx = CaptureContextFactory.build(ctx, eventType(), "OzonReturnsReadAdapter");
        List<CaptureResult> returnsPages = returnsAdapter.captureAllPages(returnsCtx, clientId, apiKey, since, to);
        results.add(subSourceRunner.processPages(
                "OzonReturnsReadAdapter", returnsPages, OzonReturnItem.class,
                batch -> returnRepo.batchUpsert(batch.stream()
                        .map(item -> mapper.toReturn(normalizer.normalizeReturn(item), ctx))
                        .toList())));

        return results;
    }
}
