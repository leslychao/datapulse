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
 * Stub EventSource for WB supply (incoming shipment) data.
 * WB categories arrive embedded in product cards, so supply data
 * is currently resolved through the product dict source.
 * Placeholder for future dedicated supply ingestion endpoint.
 */
@Slf4j
@Component
public class WbSupplyFactSource implements EventSource {

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.WB;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.SUPPLY_FACT;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext ctx) {
        log.info("WB SUPPLY_FACT skipped (no-op stub): connectionId={}", ctx.connectionId());
        return List.of(SubSourceResult.success("WbSupplyFact", 0, 0));
    }
}
