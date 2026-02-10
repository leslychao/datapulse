package io.datapulse.domain.response.account;

import io.datapulse.domain.MarketplaceType;
import java.time.OffsetDateTime;

public record AccountConnectionResponse(
    Long id,
    Long accountId,
    MarketplaceType marketplace,
    Boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    String maskedCredentials
) {

}
