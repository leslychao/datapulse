package io.datapulse.etl.dto;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;

public record ExecutionOutcome(
    String requestId,
    String rawSyncId,
    long accountId,
    String sourceId,
    MarketplaceType marketplace,
    MarketplaceEvent event,
    LocalDate dateFrom,
    LocalDate dateTo,
    IngestStatus status,
    long rowsCount,
    String errorMessage,
    Long retryAfterMillis
) {
}
