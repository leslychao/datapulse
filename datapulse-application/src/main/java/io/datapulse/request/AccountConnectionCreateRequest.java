package io.datapulse.request;

import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.validation.ConsistentMarketplace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@ConsistentMarketplace
public class AccountConnectionCreateRequest {

  @NotNull
  private Long accountId;
  @NotBlank
  private String marketplace;
  @Valid
  @NotNull
  private MarketplaceCredentials credentials;
  private Boolean active = true;
}
