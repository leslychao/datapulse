package io.datapulse.domain.response.account;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.SyncStatus;
import java.time.OffsetDateTime;

public record AccountConnectionResponse(
    Long id,
    Long accountId,
    MarketplaceType marketplace,
    Boolean active,
    String lastSyncAt,
    SyncStatus lastSyncStatus,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    String maskedCredentials
) {

}
