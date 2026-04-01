package io.datapulse.execution.persistence;

import io.datapulse.execution.domain.ActionStatus;
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
@Table(name = "price_action_state_transition")
public class PriceActionStateTransitionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "price_action_id", nullable = false)
  private Long priceActionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", nullable = false, length = 30)
  private ActionStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 30)
  private ActionStatus toStatus;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(name = "reason")
  private String reason;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = OffsetDateTime.now();
    }
  }
}
