package io.datapulse.execution.persistence;

import io.datapulse.execution.domain.AttemptOutcome;
import io.datapulse.execution.domain.ErrorClassification;
import io.datapulse.execution.domain.ReconciliationSource;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "price_action_attempt")
public class PriceActionAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price_action_id", nullable = false)
    private Long priceActionId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 30)
    private AttemptOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_classification", length = 30)
    private ErrorClassification errorClassification;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "provider_request_summary", columnDefinition = "jsonb")
    private String providerRequestSummary;

    @Column(name = "provider_response_summary", columnDefinition = "jsonb")
    private String providerResponseSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_source", length = 20)
    private ReconciliationSource reconciliationSource;

    @Column(name = "reconciliation_read_at")
    private OffsetDateTime reconciliationReadAt;

    @Column(name = "reconciliation_snapshot", columnDefinition = "jsonb")
    private String reconciliationSnapshot;

    @Column(name = "actual_price")
    private BigDecimal actualPrice;

    @Column(name = "price_match")
    private Boolean priceMatch;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
