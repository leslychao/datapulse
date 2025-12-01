package io.datapulse.core.service.vault;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public interface MarketplaceCredentialsVaultService {

  void saveCredentials(
      @Min(value = 1L, message = ValidationKeys.ACCOUNT_ID_REQUIRED)
      long accountId,

      @NotNull(message = ValidationKeys.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED)
      MarketplaceType marketplace,

      @NotNull(message = ValidationKeys.ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED)
      @Valid
      MarketplaceCredentials credentials
  );

  @NotNull
  @Valid
  MarketplaceCredentials loadCredentials(
      @Min(value = 1L, message = ValidationKeys.ACCOUNT_ID_REQUIRED)
      long accountId,

      @NotNull(message = ValidationKeys.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED)
      MarketplaceType marketplace
  );

  void deleteCredentials(
      @Min(value = 1L, message = ValidationKeys.ACCOUNT_ID_REQUIRED)
      long accountId,

      @NotNull(message = ValidationKeys.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED)
      MarketplaceType marketplace
  );
}
