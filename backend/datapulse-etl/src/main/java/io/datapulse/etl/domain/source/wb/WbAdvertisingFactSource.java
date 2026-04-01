package io.datapulse.etl.domain.source.wb;

import java.util.List;

import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub EventSource for WB advertising statistics.
 * Target design (Phase B): Raw → Normalized → ClickHouse directly (bypass canonical).
 *
 * <p>Blocked by:
 * <ul>
 *   <li>F-1: WB advertising campaigns endpoint migration (v1 → v2)</li>
 *   <li>F-2: WB fullstats v2 → v3 migration (POST → GET)</li>
 *   <li>F-4: DTO expansion for dim_advertising_campaign</li>
 *   <li>ClickHouse materializer is a stub</li>
 * </ul>
 */
@Slf4j
@Component
public class WbAdvertisingFactSource implements EventSource {

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.WB;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.ADVERTISING_FACT;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext ctx) {
        log.info("WB ADVERTISING_FACT skipped (Phase B stub): connectionId={}", ctx.connectionId());
        return List.of(SubSourceResult.success("WbAdvertisingFact", 0, 0));
    }
}
