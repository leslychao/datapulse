package io.datapulse.etl.domain.source.ozon;

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
import io.datapulse.etl.domain.EtlSubSourceResume;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalOrderUpsertRepository;
import io.datapulse.etl.persistence.canonical.CanonicalReturnUpsertRepository;
import io.datapulse.etl.persistence.canonical.CanonicalSaleUpsertRepository;
import io.datapulse.integration.domain.CredentialKeys;
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
    private final CanonicalSaleUpsertRepository saleRepo;
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
        String clientId = ctx.credentials().get(CredentialKeys.OZON_CLIENT_ID);
        String apiKey = ctx.credentials().get(CredentialKeys.OZON_API_KEY);
        var since = ctx.ozonFactSince();
        var to = ctx.ozonFactTo();
        List<SubSourceResult> results = new ArrayList<>();

        var fboCtx = CaptureContextFactory.build(ctx, eventType(), "OzonFboOrdersReadAdapter");
        long fboStart =
            EtlSubSourceResume.nonNegativeLong(ctx, eventType(), "OzonFboOrdersReadAdapter");
        List<CaptureResult> fboPages =
            fboAdapter.captureAllPages(fboCtx, clientId, apiKey, since, to, fboStart);
        results.add(subSourceRunner.processPages(
                "OzonFboOrdersReadAdapter", fboPages, OzonFboPosting.class,
                batch -> processFboBatch(batch, ctx)));

        var fbsCtx = CaptureContextFactory.build(ctx, eventType(), "OzonFbsOrdersReadAdapter");
        long fbsStart =
            EtlSubSourceResume.nonNegativeLong(ctx, eventType(), "OzonFbsOrdersReadAdapter");
        List<CaptureResult> fbsPages =
            fbsAdapter.captureAllPages(fbsCtx, clientId, apiKey, since, to, fbsStart);
        results.add(subSourceRunner.processPages(
                "OzonFbsOrdersReadAdapter", fbsPages, OzonFbsPosting.class,
                batch -> processFbsBatch(batch, ctx)));

        var returnsCtx = CaptureContextFactory.build(ctx, eventType(), "OzonReturnsReadAdapter");
        long returnsStart =
            EtlSubSourceResume.nonNegativeLong(ctx, eventType(), "OzonReturnsReadAdapter");
        List<CaptureResult> returnsPages =
            returnsAdapter.captureAllPages(returnsCtx, clientId, apiKey, since, to, returnsStart);
        results.add(subSourceRunner.processPages(
                "OzonReturnsReadAdapter", returnsPages, OzonReturnItem.class,
                batch -> returnRepo.batchUpsert(batch.stream()
                        .map(item -> mapper.toReturn(
                                normalizer.normalizeReturn(item), ctx, null, null))
                        .toList())));

        return results;
    }

    private void processFboBatch(List<OzonFboPosting> batch, IngestContext ctx) {
        List<io.datapulse.etl.persistence.canonical.CanonicalOrderEntity> orders = new ArrayList<>();
        List<io.datapulse.etl.persistence.canonical.CanonicalSaleEntity> sales = new ArrayList<>();

        for (OzonFboPosting posting : batch) {
            for (var product : posting.products()) {
                orders.add(mapper.toOrder(normalizer.normalizeFboPosting(posting, product), ctx));
                if (normalizer.isDeliveredPosting(posting.status())) {
                    sales.add(mapper.toSale(normalizer.normalizeFboSale(posting, product), ctx));
                }
            }
        }
        if (!orders.isEmpty()) {
            orderRepo.batchUpsert(orders);
        }
        if (!sales.isEmpty()) {
            saleRepo.batchUpsert(sales);
        }
    }

    private void processFbsBatch(List<OzonFbsPosting> batch, IngestContext ctx) {
        List<io.datapulse.etl.persistence.canonical.CanonicalOrderEntity> orders = new ArrayList<>();
        List<io.datapulse.etl.persistence.canonical.CanonicalSaleEntity> sales = new ArrayList<>();

        for (OzonFbsPosting posting : batch) {
            for (var product : posting.products()) {
                orders.add(mapper.toOrder(normalizer.normalizeFbsPosting(posting, product), ctx));
                if (normalizer.isDeliveredPosting(posting.status())) {
                    sales.add(mapper.toSale(normalizer.normalizeFbsSale(posting, product), ctx));
                }
            }
        }
        if (!orders.isEmpty()) {
            orderRepo.batchUpsert(orders);
        }
        if (!sales.isEmpty()) {
            saleRepo.batchUpsert(sales);
        }
    }
}
