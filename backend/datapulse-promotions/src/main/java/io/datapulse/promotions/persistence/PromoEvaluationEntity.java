package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.PromoEvaluationResult;
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
@Table(name = "promo_evaluation")
public class PromoEvaluationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "promo_evaluation_run_id", nullable = false)
    private Long promoEvaluationRunId;

    @Column(name = "canonical_promo_product_id", nullable = false)
    private Long canonicalPromoProductId;

    @Column(name = "promo_policy_id")
    private Long promoPolicyId;

    @Column(name = "evaluated_at")
    private OffsetDateTime evaluatedAt;

    @Column(name = "current_participation_status", nullable = false, length = 30)
    private String currentParticipationStatus;

    @Column(name = "promo_price")
    private BigDecimal promoPrice;

    @Column(name = "regular_price")
    private BigDecimal regularPrice;

    @Column(name = "discount_pct")
    private BigDecimal discountPct;

    @Column(name = "cogs")
    private BigDecimal cogs;

    @Column(name = "margin_at_promo_price")
    private BigDecimal marginAtPromoPrice;

    @Column(name = "margin_at_regular_price")
    private BigDecimal marginAtRegularPrice;

    @Column(name = "margin_delta_pct")
    private BigDecimal marginDeltaPct;

    @Column(name = "effective_cost_rate")
    private BigDecimal effectiveCostRate;

    @Column(name = "stock_available")
    private Integer stockAvailable;

    @Column(name = "expected_promo_duration_days")
    private Integer expectedPromoDurationDays;

    @Column(name = "avg_daily_velocity")
    private BigDecimal avgDailyVelocity;

    @Column(name = "stock_days_of_cover")
    private BigDecimal stockDaysOfCover;

    @Column(name = "stock_sufficient")
    private Boolean stockSufficient;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_result", length = 30)
    private PromoEvaluationResult evaluationResult;

    @Column(name = "signal_snapshot", columnDefinition = "jsonb")
    private String signalSnapshot;

    @Column(name = "skip_reason")
    private String skipReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
