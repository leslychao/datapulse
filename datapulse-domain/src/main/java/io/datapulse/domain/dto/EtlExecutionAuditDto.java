package io.datapulse.domain.dto;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.SyncStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EtlExecutionAuditDto extends LongBaseDto {

  private String requestId;
  private String rawSyncId;
  private Long accountId;
  private String event;
  private MarketplaceType marketplace;
  private String sourceId;
  private LocalDate dateFrom;
  private LocalDate dateTo;

  private SyncStatus status;

  private Long rowsCount;

  private String errorMessage;

  private OffsetDateTime createdAt;
}
