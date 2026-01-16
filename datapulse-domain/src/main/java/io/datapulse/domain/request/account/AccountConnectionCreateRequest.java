package io.datapulse.domain.request.account;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;

public record AccountConnectionCreateRequest(
    MarketplaceType marketplace,
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
        property = "marketplace",
        visible = true
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(value = WbCredentials.class, name = "WILDBERRIES"),
        @JsonSubTypes.Type(value = OzonCredentials.class, name = "OZON")
    })
    MarketplaceCredentials credentials
) {

}
