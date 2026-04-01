package io.datapulse.etl.domain.source.ozon;

import java.util.List;

import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub EventSource for Ozon advertising statistics.
 * Target design (Phase B): Raw → Normalized → ClickHouse directly (bypass canonical).
 *
 * <p>Blocked by:
 * <ul>
 *   <li>F-3: Ozon Performance API requires OAuth2 infrastructure</li>
 *   <li>Separate OAuth2 credentials (client_id + client_secret) needed</li>
 *   <li>Async report flow (request UUID → poll → download) not implemented</li>
 *   <li>ClickHouse materializer is a stub</li>
 * </ul>
 */
@Slf4j
@Component
public class OzonAdvertisingFactSource implements EventSource {

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.OZON;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.ADVERTISING_FACT;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext ctx) {
        log.info("Ozon ADVERTISING_FACT skipped (Phase B stub): connectionId={}", ctx.connectionId());
        return List.of(SubSourceResult.success("OzonAdvertisingFact", 0, 0));
    }
}
