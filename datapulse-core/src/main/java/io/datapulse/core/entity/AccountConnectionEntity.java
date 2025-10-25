package io.datapulse.core.entity;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.SyncStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id")
  private AccountEntity account;

  @Enumerated(EnumType.STRING)
  private MarketplaceType marketplace;

  private String credentialsEncrypted;
  private Boolean active = true;

  private OffsetDateTime lastSyncAt;

  @Enumerated(EnumType.STRING)
  private SyncStatus lastSyncStatus;

  private OffsetDateTime createdAt = OffsetDateTime.now();

  private OffsetDateTime updatedAt;
}
