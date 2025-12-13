package io.datapulse.domain.dto.request;

import static io.datapulse.domain.ValidationKeys.ACCOUNT_CONNECTION_CREDENTIALS_TYPE_MISMATCH;
import static io.datapulse.domain.ValidationKeys.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

public record AccountConnectionUpdateRequest(
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
    @Valid
    MarketplaceCredentials credentials,
    Boolean active
) {

  @AssertTrue(message = ACCOUNT_CONNECTION_CREDENTIALS_TYPE_MISMATCH)
  public boolean isTypeConsistent() {
    if (credentials == null || marketplace == null) {
      return true;
    }
    return marketplace.supports(credentials);
  }

  @AssertTrue(message = ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED)
  public boolean isMarketplacePresentWhenCredentialsProvided() {
    return credentials == null || marketplace != null;
  }
}
