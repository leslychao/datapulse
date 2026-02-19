package io.datapulse.etl.v1.dto;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;

public record ExecutionOutcome(
    String requestId,
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
