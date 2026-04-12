package io.datapulse.bidding.persistence;

import io.datapulse.bidding.domain.BiddingRunStatus;
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

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "bidding_run")
public class BiddingRunEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "workspace_id", nullable = false)
  private Long workspaceId;

  @Column(name = "bid_policy_id", nullable = false)
  private Long bidPolicyId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private BiddingRunStatus status;

  @Column(name = "total_eligible", nullable = false)
  private int totalEligible;

  @Column(name = "total_decisions", nullable = false)
  private int totalDecisions;

  @Column(name = "total_bid_up", nullable = false)
  private int totalBidUp;

  @Column(name = "total_bid_down", nullable = false)
  private int totalBidDown;

  @Column(name = "total_hold", nullable = false)
  private int totalHold;

  @Column(name = "total_pause", nullable = false)
  private int totalPause;

  @Column(name = "started_at", nullable = false)
  private OffsetDateTime startedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "error_message")
  private String errorMessage;

  @PrePersist
  protected void onCreate() {
    if (this.startedAt == null) {
      this.startedAt = OffsetDateTime.now();
    }
    if (this.status == null) {
      this.status = BiddingRunStatus.RUNNING;
    }
  }
}
