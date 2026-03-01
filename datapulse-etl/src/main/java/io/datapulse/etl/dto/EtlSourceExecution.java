package io.datapulse.etl.dto;

import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;

public record EtlSourceExecution(
    String requestId,
    long accountId,
    MarketplaceEvent event,
    String sourceId,
    LocalDate dateFrom,
    LocalDate dateTo,
    String cursor
) {
}