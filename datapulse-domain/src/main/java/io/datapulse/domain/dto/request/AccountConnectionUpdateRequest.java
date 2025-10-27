package io.datapulse.domain.dto.request;

import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED;
import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED;
import static io.datapulse.domain.MessageCodes.ACCOUNT_ID_REQUIRED;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AccountConnectionUpdateRequest(
    @NotNull(message = ACCOUNT_ID_REQUIRED)
    Long accountId,
    @NotNull(message = ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED)
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
    @NotNull(message = ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED)
    MarketplaceCredentials credentials,
    Boolean active
) {

}
