package io.datapulse.etl.flow.core.model;

import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

public record EventAggregation(
    String requestId,
    Long accountId,
    MarketplaceEvent event,
    LocalDate from,
    LocalDate to,
    EventStatus status,
    Map<String, ExecutionStatus> executionStatuses,
    Set<String> failedSources,
    boolean hasData
) {
}
