package io.datapulse.etl.dto;

import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;
import java.util.List;

public record ExecutionAggregationResult(
    String requestId,
    String rawSyncId,
    long accountId,
    MarketplaceEvent event,
    LocalDate dateFrom,
    LocalDate dateTo,
    List<ExecutionOutcome> outcomes
) {

}
