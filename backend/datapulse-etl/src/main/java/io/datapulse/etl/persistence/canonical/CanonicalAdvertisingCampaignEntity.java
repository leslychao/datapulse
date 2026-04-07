package io.datapulse.etl.persistence.canonical;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "canonical_advertising_campaign")
public class CanonicalAdvertisingCampaignEntity extends BaseEntity {

  @Column(name = "workspace_id", nullable = false)
  private Long workspaceId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(name = "source_platform", nullable = false, length = 10)
  private String sourcePlatform;

  @Column(name = "external_campaign_id", nullable = false, length = 64)
  private String externalCampaignId;

  @Column(length = 500)
  private String name;

  @Column(name = "campaign_type", nullable = false, length = 50)
  private String campaignType;

  @Column(nullable = false, length = 50)
  private String status;

  @Column(length = 100)
  private String placement;

  @Column(name = "daily_budget", precision = 18, scale = 2)
  private BigDecimal dailyBudget;

  @Column(name = "start_time")
  private OffsetDateTime startTime;

  @Column(name = "end_time")
  private OffsetDateTime endTime;

  @Column(name = "created_at_external")
  private OffsetDateTime createdAtExternal;

  @Column(name = "synced_at", nullable = false)
  private OffsetDateTime syncedAt;
}
