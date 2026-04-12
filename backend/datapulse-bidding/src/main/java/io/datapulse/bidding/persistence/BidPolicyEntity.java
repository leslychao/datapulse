package io.datapulse.bidding.persistence;

import io.datapulse.bidding.domain.BidPolicyStatus;
import io.datapulse.bidding.domain.BiddingStrategyType;
import io.datapulse.bidding.domain.ExecutionMode;
import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "bid_policy")
public class BidPolicyEntity extends BaseEntity {

  @Column(name = "workspace_id", nullable = false)
  private Long workspaceId;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "strategy_type", nullable = false, length = 50)
  private BiddingStrategyType strategyType;

  @Enumerated(EnumType.STRING)
  @Column(name = "execution_mode", nullable = false, length = 30)
  private ExecutionMode executionMode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private BidPolicyStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String config;

  @Column(name = "created_by")
  private Long createdBy;
}
