package io.datapulse.etl.flow.dto;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.event.EventSource;
import java.time.LocalDate;

public record EtlSourceExecution(
    String sourceId,
    MarketplaceEvent event,
    MarketplaceType marketplace,
    Long accountId,
    LocalDate from,
    LocalDate to,
    int order,
    EventSource source
) {

}
