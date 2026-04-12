package io.datapulse.bidding.persistence;

import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "workspace_bidding_settings")
public class WorkspaceBiddingSettingsEntity extends BaseEntity {

  @Column(name = "workspace_id", nullable = false, unique = true)
  private Long workspaceId;

  @Column(name = "bidding_enabled", nullable = false)
  private boolean biddingEnabled = true;

  @Column(name = "max_aggregate_daily_spend", precision = 12, scale = 2)
  private BigDecimal maxAggregateDailySpend;

  @Column(name = "min_decision_interval_hours", nullable = false)
  private int minDecisionIntervalHours = 4;
}
