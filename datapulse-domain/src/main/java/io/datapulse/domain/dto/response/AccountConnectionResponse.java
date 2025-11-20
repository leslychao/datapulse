package io.datapulse.domain.dto.response;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.SyncStatus;

public record AccountConnectionResponse(
    Long id,
    Long accountId,
    MarketplaceType marketplace,
    Boolean active,
    String lastSyncAt,
    SyncStatus lastSyncStatus,
    String createdAt,
    String updatedAt,
    String maskedCredentials
) {

}
