package io.datapulse.domain.dto.response;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.SyncStatus;
import java.time.OffsetDateTime;

public record AccountConnectionResponse(
    Long id,
    Long accountId,
    MarketplaceType marketplace,
    Boolean active,
    OffsetDateTime lastSyncAt,
    SyncStatus lastSyncStatus,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

}
