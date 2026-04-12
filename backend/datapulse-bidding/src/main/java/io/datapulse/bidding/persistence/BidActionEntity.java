package io.datapulse.bidding.persistence;

import io.datapulse.bidding.domain.BidActionStatus;
import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "bid_action")
public class BidActionEntity extends BaseEntity {

  @Column(name = "bid_decision_id", nullable = false)
  private Long bidDecisionId;

  @Column(name = "workspace_id", nullable = false)
  private Long workspaceId;

  @Column(name = "marketplace_offer_id", nullable = false)
  private Long marketplaceOfferId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(name = "campaign_external_id", nullable = false, length = 100)
  private String campaignExternalId;

  @Column(name = "nm_id", length = 100)
  private String nmId;

  @Column(name = "marketplace_type", nullable = false, length = 20)
  private String marketplaceType;

  @Column(name = "target_bid", nullable = false)
  private Integer targetBid;

  @Column(name = "previous_bid")
  private Integer previousBid;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private BidActionStatus status;

  @Column(name = "execution_mode", nullable = false, length = 30)
  private String executionMode;

  @Column(name = "approved_at")
  private OffsetDateTime approvedAt;

  @Column(name = "scheduled_at")
  private OffsetDateTime scheduledAt;

  @Column(name = "executed_at")
  private OffsetDateTime executedAt;

  @Column(name = "reconciled_at")
  private OffsetDateTime reconciledAt;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "max_retries", nullable = false)
  private int maxRetries;

  @Column(name = "error_message")
  private String errorMessage;
}
