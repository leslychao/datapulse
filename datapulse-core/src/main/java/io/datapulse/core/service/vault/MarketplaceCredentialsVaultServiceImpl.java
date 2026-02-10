package io.datapulse.core.service.vault;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.support.VaultResponseSupport;

@Service
@Validated
@RequiredArgsConstructor
public class MarketplaceCredentialsVaultServiceImpl implements MarketplaceCredentialsVaultService {

  private static final String ROOT_PATH = "datapulse/accounts";

  private final VaultKeyValueOperations keyValueOperations;

  @Override
  public void saveCredentials(
      long accountId,
      MarketplaceType marketplace,
      MarketplaceCredentials credentials
  ) {
    String path = buildPath(accountId, marketplace);
    keyValueOperations.put(path, credentials);
  }

  @Override
  public MarketplaceCredentials loadCredentials(
      long accountId,
      MarketplaceType marketplace
  ) {
    String path = buildPath(accountId, marketplace);

    Class<? extends MarketplaceCredentials> type = marketplace.credentialsType();

    VaultResponseSupport<? extends MarketplaceCredentials> resp = keyValueOperations.get(path,
        type);
    return resp != null ? resp.getData() : null;
  }

  @Override
  public void deleteCredentials(
      long accountId,
      MarketplaceType marketplace
  ) {
    String path = buildPath(accountId, marketplace);
    keyValueOperations.delete(path);
  }

  @Override
  public boolean credentialsExist(
      long accountId,
      MarketplaceType marketplace
  ) {
    String path = buildPath(accountId, marketplace);
    VaultResponseSupport<Object> resp = keyValueOperations.get(path, Object.class);
    return resp != null && resp.getData() != null;
  }

  private String buildPath(long accountId, MarketplaceType marketplace) {
    return ROOT_PATH + "/" + accountId + "/" + marketplace.name();
  }
}
