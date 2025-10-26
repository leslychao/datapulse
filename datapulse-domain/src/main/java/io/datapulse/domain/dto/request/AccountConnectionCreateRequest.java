package io.datapulse.domain.dto.request;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AccountConnectionCreateRequest(
    @NotNull(message = "{validation.accountConnection.accountId.notNull}")
    Long accountId,
    @NotNull(message = "{validation.accountConnection.marketplaceType.notNull}")
    MarketplaceType marketplaceType,
    @Valid
    @NotNull(message = "{validation.accountConnection.credentials.notNull}")
    MarketplaceCredentials credentials,
    Boolean active
) {

}
