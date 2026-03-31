package io.datapulse.etl.domain.source.wb;

import java.util.List;

import io.datapulse.etl.adapter.wb.WbNormalizer;
import io.datapulse.etl.adapter.wb.WbStocksReadAdapter;
import io.datapulse.etl.adapter.wb.dto.WbStockItem;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalStockCurrentUpsertRepository;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WbInventoryFactSource implements EventSource {

    private final WbStocksReadAdapter adapter;
    private final WbNormalizer normalizer;
    private final CanonicalStockCurrentUpsertRepository repository;
    private final CanonicalEntityMapper mapper;
    private final SubSourceRunner subSourceRunner;

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.WB;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.INVENTORY_FACT;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext ctx) {
        String token = ctx.credentials().get("apiToken");
        var captureCtx = CaptureContextFactory.build(ctx, eventType(), "WbStocksReadAdapter");
        List<CaptureResult> pages = adapter.captureAllPages(captureCtx, token);

        SubSourceResult result = subSourceRunner.processPages(
                "WbStocksReadAdapter", pages, WbStockItem.class,
                batch -> repository.batchUpsert(batch.stream()
                        .map(item -> mapper.toStock(normalizer.normalizeStock(item), ctx))
                        .toList()));
        return List.of(result);
    }
}
