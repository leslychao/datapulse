package io.datapulse.etl.nextgen.dto;

import java.time.OffsetDateTime;

public record EventAuditRecord(
    String eventId,
    Long accountId,
    String eventType,
    EventStatus status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    long totalRawRows,
    int successExecutions,
    int noDataExecutions,
    int failedExecutions
) {
}
