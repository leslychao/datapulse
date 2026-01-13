package io.datapulse.etl.dto;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.event.EventSource;
import java.time.LocalDate;

public record EtlSourceExecution(
    String requestId,
    String sourceId,
    MarketplaceEvent event,
    MarketplaceType marketplace,
    Long accountId,
    LocalDate dateFrom,
    LocalDate dateTo,
    int order,
    EventSource source,
    String rawTable
) {

}
