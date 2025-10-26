package io.datapulse.domain.dto.credentials;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
    property = "marketplaceType"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = WbCredentials.class, name = "WILDBERRIES"),
    @JsonSubTypes.Type(value = OzonCredentials.class, name = "OZON")
})
public sealed interface MarketplaceCredentials permits WbCredentials, OzonCredentials {

}
