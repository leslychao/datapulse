package io.datapulse.execution.persistence;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionReconciliationSource;
import io.datapulse.execution.domain.ActionStatus;
import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "price_action")
public class PriceActionEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "marketplace_offer_id", nullable = false)
    private Long marketplaceOfferId;

    @Column(name = "price_decision_id", nullable = false)
    private Long priceDecisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 20)
    private ActionExecutionMode executionMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ActionStatus status;

    @Column(name = "target_price", nullable = false)
    private BigDecimal targetPrice;

    @Column(name = "current_price_at_creation", nullable = false)
    private BigDecimal currentPriceAtCreation;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "hold_reason")
    private String holdReason;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "superseded_by_action_id")
    private Long supersededByActionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_source", length = 10)
    private ActionReconciliationSource reconciliationSource;

    @Column(name = "manual_override_reason")
    private String manualOverrideReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "approval_timeout_hours", nullable = false)
    private int approvalTimeoutHours;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;
}
