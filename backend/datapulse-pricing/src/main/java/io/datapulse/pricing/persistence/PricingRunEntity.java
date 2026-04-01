package io.datapulse.pricing.persistence;

import io.datapulse.pricing.domain.RunStatus;
import io.datapulse.pricing.domain.RunTriggerType;
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
@Table(name = "pricing_run")
public class PricingRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private RunTriggerType triggerType;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "requested_offers_count")
    private Integer requestedOffersCount;

    @Column(name = "source_job_execution_id")
    private Long sourceJobExecutionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RunStatus status;

    @Column(name = "total_offers")
    private Integer totalOffers;

    @Column(name = "eligible_count")
    private Integer eligibleCount;

    @Column(name = "change_count")
    private Integer changeCount;

    @Column(name = "skip_count")
    private Integer skipCount;

    @Column(name = "hold_count")
    private Integer holdCount;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
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
            this.status = RunStatus.PENDING;
        }
    }
}
