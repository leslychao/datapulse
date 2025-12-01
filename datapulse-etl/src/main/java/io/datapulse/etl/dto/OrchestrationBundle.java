package io.datapulse.etl.dto;

import io.datapulse.domain.SyncStatus;
import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;
import java.util.List;

public record OrchestrationBundle(
    String requestId,
    Long accountId,
    MarketplaceEvent event,
    LocalDate dateFrom,
    LocalDate dateTo,
    SyncStatus syncStatus,
    String failedSourceIds,
    String errorMessage,
    List<IngestResult> results,
    Integer retryAfterSeconds
) {

}
