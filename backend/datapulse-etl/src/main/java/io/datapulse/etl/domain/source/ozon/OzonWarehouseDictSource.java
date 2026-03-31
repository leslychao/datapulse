package io.datapulse.etl.domain.source.ozon;

import java.util.List;

import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.integration.domain.MarketplaceType;
import org.springframework.stereotype.Component;

/**
 * Ozon has no dedicated warehouse endpoint — warehouses are
 * discovered from stock responses during INVENTORY_FACT processing.
 */
@Component
public class OzonWarehouseDictSource implements EventSource {

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.OZON;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.WAREHOUSE_DICT;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext context) {
        return List.of(SubSourceResult.success("OzonWarehouseDict", 0, 0));
    }
}
