package io.datapulse.etl.dto;

import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;
import java.util.List;

public record OrchestrationCommand(
    String requestId,
    Long accountId,
    MarketplaceEvent event,
    LocalDate dateFrom,
    LocalDate dateTo,
    List<String> sourceIds
) {

}
