package io.datapulse.etl.domain.source.wb;

import java.util.List;

import io.datapulse.etl.adapter.wb.WbNormalizer;
import io.datapulse.etl.adapter.wb.WbWarehousesReadAdapter;
import io.datapulse.etl.adapter.wb.dto.WbOffice;
import io.datapulse.etl.domain.CanonicalEntityMapper;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.WarehouseUpsertRepository;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WbWarehouseDictSource implements EventSource {

    private final WbWarehousesReadAdapter adapter;
    private final WbNormalizer normalizer;
    private final WarehouseUpsertRepository repository;
    private final CanonicalEntityMapper mapper;
    private final SubSourceRunner subSourceRunner;

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.WB;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.WAREHOUSE_DICT;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext ctx) {
        String token = ctx.credentials().get("apiToken");
        var captureCtx = CaptureContextFactory.build(ctx, eventType(), "WbWarehousesReadAdapter");
        CaptureResult page = adapter.capturePage(captureCtx, token);

        SubSourceResult result = subSourceRunner.processPages(
                "WbWarehousesReadAdapter", List.of(page), WbOffice.class,
                batch -> repository.batchUpsert(batch.stream()
                        .map(office -> mapper.toWarehouse(normalizer.normalizeWarehouse(office), ctx))
                        .toList()));
        return List.of(result);
    }
}
