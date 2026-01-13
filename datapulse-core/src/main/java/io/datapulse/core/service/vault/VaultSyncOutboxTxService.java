package io.datapulse.core.service.vault;

import io.datapulse.core.entity.account.AccountConnectionEntity;
import io.datapulse.core.entity.account.VaultSyncCommandType;
import io.datapulse.core.entity.account.VaultSyncOutboxEntity;
import io.datapulse.core.entity.account.VaultSyncStatus;
import io.datapulse.core.repository.account.AccountConnectionRepository;
import io.datapulse.core.repository.VaultSyncOutboxRepository;
import io.datapulse.domain.CommonConstants;
import io.datapulse.domain.MarketplaceType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class VaultSyncOutboxTxService {

  private final VaultSyncOutboxRepository outboxRepository;
  private final AccountConnectionRepository accountConnectionRepository;

  VaultSyncOutboxTxService(
      VaultSyncOutboxRepository outboxRepository,
      AccountConnectionRepository accountConnectionRepository
  ) {
    this.outboxRepository = outboxRepository;
    this.accountConnectionRepository = accountConnectionRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public VaultSyncOutboxEntity upsertPending(long accountId, MarketplaceType marketplace,
      VaultSyncCommandType commandType) {
    OffsetDateTime now = now();

    Optional<VaultSyncOutboxEntity> existing =
        outboxRepository.findByAccountIdAndMarketplaceAndCommandType(accountId, marketplace,
            commandType);

    if (existing.isPresent()) {
      VaultSyncOutboxEntity outbox = existing.get();
      outbox.setStatus(VaultSyncStatus.PENDING);
      outbox.setAttempts(0);
      outbox.setNextAttemptAt(now);
      outbox.setUpdatedAt(now);
      return outboxRepository.save(outbox);
    }

    VaultSyncOutboxEntity outbox = new VaultSyncOutboxEntity();
    outbox.setAccountId(accountId);
    outbox.setMarketplace(marketplace);
    outbox.setCommandType(commandType);
    outbox.setStatus(VaultSyncStatus.PENDING);
    outbox.setAttempts(0);
    outbox.setNextAttemptAt(now);
    outbox.setCreatedAt(now);
    outbox.setUpdatedAt(now);
    return outboxRepository.save(outbox);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markCompleted(long outboxId) {
    VaultSyncOutboxEntity outbox = outboxRepository.findById(outboxId).orElse(null);
    if (outbox == null) {
      return;
    }
    if (outbox.getStatus() == VaultSyncStatus.COMPLETED) {
      return;
    }

    OffsetDateTime now = now();
    outbox.setStatus(VaultSyncStatus.COMPLETED);
    outbox.setAttempts(0);
    outbox.setNextAttemptAt(null);
    outbox.setUpdatedAt(now);
    outboxRepository.save(outbox);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markRetry(long outboxId) {
    VaultSyncOutboxEntity outbox = outboxRepository.findById(outboxId).orElse(null);
    if (outbox == null) {
      return;
    }

    OffsetDateTime now = now();
    outbox.setAttempts(outbox.getAttempts() + 1);
    outbox.setStatus(VaultSyncStatus.RETRY);
    outbox.setNextAttemptAt(now);
    outbox.setUpdatedAt(now);
    outboxRepository.save(outbox);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void activateAccountConnectionIfPresent(long accountId, MarketplaceType marketplace) {
    Optional<AccountConnectionEntity> existing =
        accountConnectionRepository.findByAccount_IdAndMarketplace(accountId, marketplace);

    if (existing.isEmpty()) {
      return;
    }

    AccountConnectionEntity connection = existing.get();
    if (Boolean.TRUE.equals(connection.getActive())) {
      return;
    }

    OffsetDateTime now = now();
    connection.setActive(true);
    connection.setUpdatedAt(now);
    accountConnectionRepository.save(connection);
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(CommonConstants.ZONE_ID_DEFAULT);
  }
}
