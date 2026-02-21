package io.datapulse.etl.v1.dto;

import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;

public record EtlSourceExecution(
    String requestId,
    Long accountId,
    MarketplaceEvent event,
    String sourceId,
    LocalDate dateFrom,
    LocalDate dateTo
) {
}
