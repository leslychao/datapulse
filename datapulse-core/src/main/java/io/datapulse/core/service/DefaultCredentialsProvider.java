package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.ACCOUNT_CONNECTION_INVALID_JSON;

import io.datapulse.core.service.vault.MarketplaceCredentialsVaultService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultCredentialsProvider implements CredentialsProvider {

  private final AccountConnectionService accountConnectionService;
  private final MarketplaceCredentialsVaultService vaultService;

  @Override
  @Transactional(readOnly = true)
  public MarketplaceCredentials resolve(long accountId, MarketplaceType type) {
    accountConnectionService.assertActiveConnectionExists(accountId, type);

    MarketplaceCredentials credentials = vaultService.loadCredentials(accountId, type);
    if (credentials == null) {
      throw new AppException(ACCOUNT_CONNECTION_INVALID_JSON);
    }

    return credentials;
  }
}
