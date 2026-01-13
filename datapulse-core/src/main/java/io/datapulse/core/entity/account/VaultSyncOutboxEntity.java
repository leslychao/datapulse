package io.datapulse.core.entity.account;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.domain.MarketplaceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "vault_sync_outbox")
@Getter
@Setter
public class VaultSyncOutboxEntity extends LongBaseEntity {

  @Column(name = "account_id")
  private Long accountId;

  @Enumerated(EnumType.STRING)
  private MarketplaceType marketplace;

  @Enumerated(EnumType.STRING)
  private VaultSyncCommandType commandType;

  @Enumerated(EnumType.STRING)
  private VaultSyncStatus status = VaultSyncStatus.PENDING;

  private int attempts;

  private OffsetDateTime nextAttemptAt;

  private OffsetDateTime createdAt = OffsetDateTime.now();

  private OffsetDateTime updatedAt = OffsetDateTime.now();
}
