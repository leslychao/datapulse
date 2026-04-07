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
 * Stub EventSource for Ozon supply data.
 * Ozon has no direct supply/inbound shipment API equivalent to WB incomes.
 * Placeholder for future implementation if Ozon introduces such an endpoint.
 */
@Slf4j
@Component
public class OzonSupplyFactSource implements EventSource {

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.OZON;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.SUPPLY_FACT;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext ctx) {
        log.info("Ozon SUPPLY_FACT skipped (no-op stub, no Ozon API equivalent): connectionId={}",
                ctx.connectionId());
        return List.of(SubSourceResult.success("OzonSupplyFact", 0, 0));
    }
}
