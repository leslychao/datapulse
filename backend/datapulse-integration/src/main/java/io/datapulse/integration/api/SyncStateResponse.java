package io.datapulse.integration.api;

import java.time.OffsetDateTime;

public record SyncStateResponse(
        String dataDomain,
        OffsetDateTime lastSyncAt,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime nextScheduledAt,
        String status
) {
}
