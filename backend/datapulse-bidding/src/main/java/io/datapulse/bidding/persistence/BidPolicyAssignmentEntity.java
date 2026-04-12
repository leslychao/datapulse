package io.datapulse.bidding.persistence;

import io.datapulse.bidding.domain.AssignmentScope;
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
@Table(name = "bid_policy_assignment")
public class BidPolicyAssignmentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "bid_policy_id", nullable = false)
  private Long bidPolicyId;

  @Column(name = "workspace_id", nullable = false)
  private Long workspaceId;

  @Column(name = "marketplace_offer_id")
  private Long marketplaceOfferId;

  @Column(name = "campaign_external_id", length = 100)
  private String campaignExternalId;

  @Enumerated(EnumType.STRING)
  @Column(name = "assignment_scope", nullable = false, length = 30)
  private AssignmentScope assignmentScope;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = OffsetDateTime.now();
    }
  }
}
