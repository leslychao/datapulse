package io.datapulse.core.entity.account;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.SyncStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "account_connection")
@Getter
@Setter
public class AccountConnectionEntity extends LongBaseEntity {

  @ManyToOne
  @JoinColumn(name = "account_id", nullable = false)
  private AccountEntity account;

  @Enumerated(EnumType.STRING)
  private MarketplaceType marketplace;

  private boolean active;

  private String maskedCredentials;

  private OffsetDateTime lastSyncAt;

  @Enumerated(EnumType.STRING)
  private SyncStatus lastSyncStatus = SyncStatus.NEW;
}
