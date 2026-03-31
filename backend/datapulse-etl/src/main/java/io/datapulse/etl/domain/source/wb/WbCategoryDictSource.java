package io.datapulse.etl.domain.source.wb;

import java.util.List;

import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.integration.domain.MarketplaceType;
import org.springframework.stereotype.Component;

/**
 * WB has no separate category endpoint — categories are extracted
 * from catalog cards during PRODUCT_DICT processing.
 */
@Component
public class WbCategoryDictSource implements EventSource {

    @Override
    public MarketplaceType marketplace() {
        return MarketplaceType.WB;
    }

    @Override
    public EtlEventType eventType() {
        return EtlEventType.CATEGORY_DICT;
    }

    @Override
    public List<SubSourceResult> execute(IngestContext context) {
        return List.of(SubSourceResult.success("WbCategoryDict", 0, 0));
    }
}
