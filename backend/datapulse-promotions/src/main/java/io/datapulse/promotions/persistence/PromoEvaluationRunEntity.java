package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.PromoRunStatus;
import io.datapulse.promotions.domain.PromoRunTriggerType;
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
@Table(name = "promo_evaluation_run")
public class PromoEvaluationRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private PromoRunTriggerType triggerType;

    @Column(name = "source_job_execution_id")
    private Long sourceJobExecutionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromoRunStatus status;

    @Column(name = "total_products")
    private Integer totalProducts;

    @Column(name = "eligible_count")
    private Integer eligibleCount;

    @Column(name = "participate_count")
    private Integer participateCount;

    @Column(name = "decline_count")
    private Integer declineCount;

    @Column(name = "pending_review_count")
    private Integer pendingReviewCount;

    @Column(name = "deactivate_count")
    private Integer deactivateCount;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "error_details", columnDefinition = "jsonb")
    private String errorDetails;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.status == null) {
            this.status = PromoRunStatus.PENDING;
        }
    }
}
