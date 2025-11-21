package io.datapulse.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "etl_sync_audit")
@Getter
@Setter
public class EtlSyncAuditEntity extends LongBaseEntity {

  private String requestId;
  private Long accountId;
  private String event;
  private LocalDate dateFrom;
  private LocalDate dateTo;
}
