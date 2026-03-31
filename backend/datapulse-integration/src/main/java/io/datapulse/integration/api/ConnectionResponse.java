package io.datapulse.integration.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ConnectionResponse(
        Long id,
        String marketplaceType,
        String name,
        String status,
        String externalAccountId,
        OffsetDateTime lastCheckAt,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime lastErrorAt,
        String lastErrorCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<SyncStateResponse> syncStates
) {
}
