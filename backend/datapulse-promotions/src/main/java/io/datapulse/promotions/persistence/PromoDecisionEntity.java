package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.ParticipationMode;
import io.datapulse.promotions.domain.PromoDecisionType;
import io.datapulse.promotions.domain.PromoExecutionMode;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "promo_decision")
public class PromoDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "canonical_promo_product_id", nullable = false)
    private Long canonicalPromoProductId;

    @Column(name = "promo_evaluation_id")
    private Long promoEvaluationId;

    @Column(name = "policy_version", nullable = false)
    private Integer policyVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot", nullable = false, columnDefinition = "jsonb")
    private String policySnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", nullable = false, length = 30)
    private PromoDecisionType decisionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_mode", nullable = false, length = 20)
    private ParticipationMode participationMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 20)
    private PromoExecutionMode executionMode;

    @Column(name = "target_promo_price")
    private BigDecimal targetPromoPrice;

    @Column(name = "explanation_summary")
    private String explanationSummary;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
