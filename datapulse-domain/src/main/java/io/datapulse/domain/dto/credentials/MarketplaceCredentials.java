package io.datapulse.domain.dto.credentials;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = WbCredentials.class, name = "WB"),
    @JsonSubTypes.Type(value = OzonCredentials.class, name = "OZON")
})
public sealed interface MarketplaceCredentials permits WbCredentials, OzonCredentials {

  String type();
}
