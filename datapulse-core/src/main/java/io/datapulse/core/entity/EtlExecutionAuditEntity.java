package io.datapulse.core.entity;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.SyncStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "etl_execution_audit")
@Getter
@Setter
public class EtlExecutionAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

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

  @PrePersist
  protected void onCreate() {
    this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }
}
