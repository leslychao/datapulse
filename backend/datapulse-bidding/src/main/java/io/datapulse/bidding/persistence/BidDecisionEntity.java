package io.datapulse.bidding.persistence;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingStrategyType;
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
@Table(name = "bid_decision")
public class BidDecisionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "bidding_run_id", nullable = false)
  private Long biddingRunId;

  @Column(name = "workspace_id", nullable = false)
  private Long workspaceId;

  @Column(name = "marketplace_offer_id", nullable = false)
  private Long marketplaceOfferId;

  @Column(name = "bid_policy_id", nullable = false)
  private Long bidPolicyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "strategy_type", nullable = false, length = 50)
  private BiddingStrategyType strategyType;

  @Enumerated(EnumType.STRING)
  @Column(name = "decision_type", nullable = false, length = 30)
  private BidDecisionType decisionType;

  @Column(name = "current_bid")
  private Integer currentBid;

  @Column(name = "target_bid")
  private Integer targetBid;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "signal_snapshot", columnDefinition = "jsonb")
  private String signalSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "guards_applied", columnDefinition = "jsonb")
  private String guardsApplied;

  @Column(name = "explanation_summary")
  private String explanationSummary;

  @Column(name = "execution_mode", nullable = false, length = 30)
  private String executionMode;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = OffsetDateTime.now();
    }
  }
}
