package io.datapulse.core.service.vault;

import io.datapulse.core.entity.account.VaultSyncCommandType;
import io.datapulse.core.entity.account.VaultSyncOutboxEntity;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Slf4j
public class VaultSyncOutboxService {

  private final MarketplaceCredentialsVaultService vaultService;
  private final VaultSyncOutboxTxService txService;

  public VaultSyncOutboxService(
      MarketplaceCredentialsVaultService vaultService,
      VaultSyncOutboxTxService txService
  ) {
    this.vaultService = vaultService;
    this.txService = txService;
  }

  public void ensurePresent(
      @Min(value = 1L, message = ValidationKeys.ACCOUNT_ID_REQUIRED)
      long accountId,
      @NotNull(message = ValidationKeys.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED)
      MarketplaceType marketplace,
      @NotNull(message = ValidationKeys.ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED)
      @Valid
      MarketplaceCredentials credentials
  ) {
    VaultSyncOutboxEntity outbox = txService.upsertPending(
        accountId,
        marketplace,
        VaultSyncCommandType.ENSURE_PRESENT
    );

    try {
      vaultService.saveCredentials(accountId, marketplace, credentials);
      txService.markCompleted(outbox.getId());
      txService.activateAccountConnectionIfPresent(accountId, marketplace);
    } catch (RuntimeException e) {
      txService.markRetry(outbox.getId());
      log.warn(
          "Failed to save marketplace credentials to Vault: accountId={}, marketplace={}",
          accountId,
          marketplace,
          e
      );
    }
  }

  public void ensureAbsent(
      @Min(value = 1L, message = ValidationKeys.ACCOUNT_ID_REQUIRED)
      long accountId,
      @NotNull(message = ValidationKeys.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED)
      MarketplaceType marketplace
  ) {
    VaultSyncOutboxEntity outbox = txService.upsertPending(
        accountId,
        marketplace,
        VaultSyncCommandType.ENSURE_ABSENT
    );

    try {
      vaultService.deleteCredentials(accountId, marketplace);
      txService.markCompleted(outbox.getId());
    } catch (RuntimeException e) {
      txService.markRetry(outbox.getId());
      log.warn(
          "Failed to delete marketplace credentials from Vault: accountId={}, marketplace={}",
          accountId,
          marketplace,
          e
      );
    }
  }

  public boolean isPresent(
      @Min(value = 1L, message = ValidationKeys.ACCOUNT_ID_REQUIRED)
      long accountId,
      @NotNull(message = ValidationKeys.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED)
      MarketplaceType marketplace
  ) {
    return vaultService.credentialsExist(accountId, marketplace);
  }
}
