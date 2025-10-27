package io.datapulse.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AccountConnectionUpdateRequest(
    @NotNull(message = "{validation.accountConnection.accountId.notNull}")
    Long accountId,
    @NotNull(message = "{validation.accountConnection.marketplaceType.notNull}")
    MarketplaceType marketplaceType,

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
        property = "marketplaceType",
        visible = true
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(value = WbCredentials.class, name = "WILDBERRIES"),
        @JsonSubTypes.Type(value = OzonCredentials.class, name = "OZON")
    })
    @Valid
    @NotNull(message = "{validation.accountConnection.credentials.notNull}")
    MarketplaceCredentials credentials,
    Boolean active
) {

}
