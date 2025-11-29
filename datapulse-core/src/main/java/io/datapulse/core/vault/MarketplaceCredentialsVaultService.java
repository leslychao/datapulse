package io.datapulse.core.vault;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import lombok.NonNull;

public interface MarketplaceCredentialsVaultService {

  void saveCredentials(
      long accountId,
      @NonNull MarketplaceType marketplace,
      @NonNull MarketplaceCredentials credentials
  );

  MarketplaceCredentials loadCredentials(
      long accountId,
      @NonNull MarketplaceType marketplace
  );

  void deleteCredentials(
      long accountId,
      @NonNull MarketplaceType marketplace
  );
}
