package io.datapulse.etl.dto;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.IngestStatus;

public record ExecutionOutcome(
    String requestId,
    long accountId,
    String sourceId,
    MarketplaceType marketplace,
    MarketplaceEvent event,
    IngestStatus status,
    long rowsCount,
    String errorMessage,
    Long retryAfterMillis
) {
}
