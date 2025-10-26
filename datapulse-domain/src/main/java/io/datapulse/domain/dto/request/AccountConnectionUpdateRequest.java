package io.datapulse.domain.dto.request;

import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import jakarta.validation.Valid;
import lombok.Data;

@Data
public class AccountConnectionUpdateRequest {

  @Valid
  private MarketplaceCredentials credentials;
  private Boolean active;
}
