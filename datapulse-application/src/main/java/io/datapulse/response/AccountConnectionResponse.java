package io.datapulse.response;

import io.datapulse.domain.MarketplaceType;
import lombok.Data;

@Data
public class AccountConnectionResponse {

  private Long id;
  private Long accountId;
  private MarketplaceType marketplace;
  private String credentialsPreview;
  private Boolean active;
  private String lastSyncStatus;
  private String lastSyncAt;
  private String createdAt;
  private String updatedAt;
}
