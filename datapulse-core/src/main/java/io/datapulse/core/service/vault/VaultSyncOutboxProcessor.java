package io.datapulse.core.service.vault;

import io.datapulse.core.entity.account.AccountConnectionEntity;
import io.datapulse.core.entity.account.VaultSyncCommandType;
import io.datapulse.core.entity.account.VaultSyncOutboxEntity;
import io.datapulse.core.entity.account.VaultSyncStatus;
import io.datapulse.core.repository.VaultSyncOutboxRepository;
import io.datapulse.core.repository.account.AccountConnectionRepository;
import io.datapulse.domain.CommonConstants;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VaultSyncOutboxProcessor {

  private static final Logger log = LoggerFactory.getLogger(VaultSyncOutboxProcessor.class);

  private static final int MAX_ATTEMPTS = 10;
  private static final int BATCH_SIZE = 50;
  private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);

  private final VaultSyncOutboxRepository repository;
  private final MarketplaceCredentialsVaultService vaultService;
  private final AccountConnectionRepository accountConnectionRepository;

  public VaultSyncOutboxProcessor(
      VaultSyncOutboxRepository repository,
      MarketplaceCredentialsVaultService vaultService,
      AccountConnectionRepository accountConnectionRepository
  ) {
    this.repository = repository;
    this.vaultService = vaultService;
    this.accountConnectionRepository = accountConnectionRepository;
  }

  @Scheduled(fixedDelayString = "60000", initialDelayString = "60000")
  @Transactional
  public void process() {
    OffsetDateTime now = OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT);
    Set<VaultSyncStatus> statuses = EnumSet.of(VaultSyncStatus.PENDING, VaultSyncStatus.RETRY);
    List<VaultSyncOutboxEntity> batch = repository.lockNextBatch(
        statuses,
        now,
        PageRequest.of(0, BATCH_SIZE)
    );

    for (VaultSyncOutboxEntity outbox : batch) {
      processOutbox(outbox, now);
    }
  }

  private void processOutbox(VaultSyncOutboxEntity outbox, OffsetDateTime now) {
    boolean success;
    VaultSyncCommandType commandType = outbox.getCommandType();
    MarketplaceType marketplace = outbox.getMarketplace();
    Long accountId = outbox.getAccountId();

    try {
      if (commandType == VaultSyncCommandType.ENSURE_PRESENT) {
        MarketplaceCredentials credentials = vaultService.loadCredentials(accountId, marketplace);
        success = credentials != null;
      } else {
        vaultService.deleteCredentials(accountId, marketplace);
        success = true;
      }
    } catch (Exception e) {
      success = false;
      log.warn("Vault sync attempt failed for account {} marketplace {} command {}", accountId,
          marketplace, commandType, e);
    }

    if (success) {
      markCompleted(outbox, now);
      if (commandType == VaultSyncCommandType.ENSURE_PRESENT) {
        activateAccountConnection(accountId, marketplace, now);
      }
      return;
    }

    int attempts = outbox.getAttempts() + 1;
    outbox.setAttempts(attempts);

    if (attempts >= MAX_ATTEMPTS) {
      outbox.setStatus(VaultSyncStatus.FAILED);
      outbox.setNextAttemptAt(null);
      outbox.setUpdatedAt(now);
      repository.save(outbox);
      if (commandType == VaultSyncCommandType.ENSURE_PRESENT) {
        deactivateAccountConnection(accountId, marketplace, now);
      }
      return;
    }

    outbox.setStatus(VaultSyncStatus.RETRY);
    outbox.setNextAttemptAt(now.plus(backoffForAttempt(attempts)));
    outbox.setUpdatedAt(now);
    repository.save(outbox);
  }

  private void markCompleted(VaultSyncOutboxEntity outbox, OffsetDateTime now) {
    outbox.setStatus(VaultSyncStatus.COMPLETED);
    outbox.setAttempts(0);
    outbox.setNextAttemptAt(null);
    outbox.setUpdatedAt(now);
    repository.save(outbox);
  }

  private Duration backoffForAttempt(int attempt) {
    long multiplier = Math.max(1L, attempt);
    Duration backoff = BASE_BACKOFF.multipliedBy(multiplier);
    Duration maxBackoff = Duration.ofHours(1);
    if (backoff.compareTo(maxBackoff) > 0) {
      return maxBackoff;
    }
    return backoff;
  }

  private void deactivateAccountConnection(
      Long accountId,
      MarketplaceType marketplace,
      OffsetDateTime now
  ) {
    Optional<AccountConnectionEntity> connection = accountConnectionRepository
        .findByAccount_IdAndMarketplace(accountId, marketplace);

    if (connection.isEmpty()) {
      log.warn("Account connection not found for account {} marketplace {}", accountId,
          marketplace);
      return;
    }

    AccountConnectionEntity entity = connection.get();
    entity.setActive(false);
    entity.setUpdatedAt(now);
    accountConnectionRepository.save(entity);
  }

  private void activateAccountConnection(
      Long accountId,
      MarketplaceType marketplace,
      OffsetDateTime now
  ) {
    Optional<AccountConnectionEntity> connection = accountConnectionRepository
        .findByAccount_IdAndMarketplace(accountId, marketplace);

    if (connection.isEmpty()) {
      log.warn("Account connection not found for account {} marketplace {}", accountId,
          marketplace);
      return;
    }

    AccountConnectionEntity entity = connection.get();
    if (entity.isActive()) {
      return;
    }

    entity.setActive(true);
    entity.setUpdatedAt(now);
    accountConnectionRepository.save(entity);
  }
}
