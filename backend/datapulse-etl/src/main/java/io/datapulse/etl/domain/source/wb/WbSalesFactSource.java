package io.datapulse.etl.domain.source.wb;

import java.util.ArrayList;
import java.util.List;

import io.datapulse.etl.adapter.wb.WbNormalizer;
import io.datapulse.etl.adapter.wb.WbOrdersReadAdapter;
import io.datapulse.etl.adapter.wb.WbReturnsReadAdapter;
import io.datapulse.etl.adapter.wb.WbSalesReadAdapter;
import io.datapulse.etl.adapter.wb.dto.WbOrderItem;
import io.datapulse.etl.adapter.wb.dto.WbReturnItem;
import io.datapulse.etl.adapter.wb.dto.WbSaleItem;
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
import io.datapulse.etl.persistence.canonical.CanonicalSaleUpsertRepository;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WbSalesFactSource implements EventSource {

    private final WbOrdersReadAdapter ordersAdapter;
    private final WbSalesReadAdapter salesAdapter;
    private final WbReturnsReadAdapter returnsAdapter;
    private final WbNormalizer normalizer;
    private final CanonicalOrderUpsertRepository orderRepo;
    private final CanonicalSaleUpsertRepository saleRepo;
    private final CanonicalReturnUpsertRepository returnRepo;
    private final CanonicalEntityMapper mapper;
    private final SubSourceRunner subSourceRunner;

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.WB;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.SALES_FACT;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext ctx) {
        String token = ctx.credentials().get(CredentialKeys.WB_API_TOKEN);
        var dateFrom = ctx.wbFactDateFrom();
        var dateTo = ctx.wbFactDateTo();
        List<SubSourceResult> results = new ArrayList<>();

        var ordersCtx = CaptureContextFactory.build(ctx, eventType(), "WbOrdersReadAdapter");
        CaptureResult ordersPage = ordersAdapter.capturePage(ordersCtx, token, dateFrom, 0);
        results.add(subSourceRunner.processPages(
                "WbOrdersReadAdapter", List.of(ordersPage), WbOrderItem.class,
                batch -> orderRepo.batchUpsert(batch.stream()
                        .map(item -> mapper.toOrder(normalizer.normalizeOrder(item), ctx))
                        .toList())));

        var salesCtx = CaptureContextFactory.build(ctx, eventType(), "WbSalesReadAdapter");
        CaptureResult salesPage = salesAdapter.capturePage(salesCtx, token, dateFrom, 0);
        results.add(subSourceRunner.processPages(
                "WbSalesReadAdapter", List.of(salesPage), WbSaleItem.class,
                batch -> saleRepo.batchUpsert(batch.stream()
                        .map(item -> mapper.toSale(normalizer.normalizeSale(item), ctx))
                        .toList())));

        var returnsCtx = CaptureContextFactory.build(ctx, eventType(), "WbReturnsReadAdapter");
        CaptureResult returnsPage = returnsAdapter.capturePage(returnsCtx, token, dateFrom, dateTo);
        results.add(subSourceRunner.processPages(
                "WbReturnsReadAdapter", List.of(returnsPage), WbReturnItem.class,
                batch -> returnRepo.batchUpsert(batch.stream()
                        .map(item -> mapper.toReturn(normalizer.normalizeReturn(item), ctx))
                        .toList())));

        return results;
    }
}
