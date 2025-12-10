package io.datapulse.core.entity;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.SyncStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "etl_execution_audit")
@Getter
@Setter
public class EtlExecutionAuditEntity extends LongBaseEntity {

  private String requestId;
  private Long accountId;
  private String event;

  @Enumerated(EnumType.STRING)
  private MarketplaceType marketplace;

  private String sourceId;

  private LocalDate dateFrom;
  private LocalDate dateTo;

  @Enumerated(EnumType.STRING)
  private SyncStatus status;

  private Long rowsCount;

  private String errorMessage;
}
