package io.datapulse.bidding.persistence;

import io.datapulse.bidding.domain.AttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "bid_action_attempt")
public class BidActionAttemptEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "bid_action_id", nullable = false)
  private Long bidActionId;

  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_summary", columnDefinition = "jsonb")
  private String requestSummary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_summary", columnDefinition = "jsonb")
  private String responseSummary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "reconciliation_read", columnDefinition = "jsonb")
  private String reconciliationRead;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private AttemptStatus status;

  @Column(name = "error_code", length = 100)
  private String errorCode;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = OffsetDateTime.now();
    }
  }
}
