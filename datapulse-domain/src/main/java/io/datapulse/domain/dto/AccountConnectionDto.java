package io.datapulse.domain.dto;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.SyncStatus;
import java.time.OffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class AccountConnectionDto extends LongBaseDto {

  private Long accountId;
  private MarketplaceType marketplace;
  private String credentialsEncrypted;
  private Boolean active;
  private OffsetDateTime lastSyncAt;
  private SyncStatus lastSyncStatus;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
