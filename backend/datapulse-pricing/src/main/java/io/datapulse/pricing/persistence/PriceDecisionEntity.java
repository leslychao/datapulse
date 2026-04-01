package io.datapulse.pricing.persistence;

import io.datapulse.pricing.domain.DecisionType;
import io.datapulse.pricing.domain.PolicyType;
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
@Table(name = "price_decision")
public class PriceDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "pricing_run_id", nullable = false)
    private Long pricingRunId;

    @Column(name = "marketplace_offer_id", nullable = false)
    private Long marketplaceOfferId;

    @Column(name = "price_policy_id")
    private Long pricePolicyId;

    @Column(name = "policy_version", nullable = false)
    private Integer policyVersion;

    @Column(name = "policy_snapshot", columnDefinition = "jsonb")
    private String policySnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", nullable = false, length = 20)
    private DecisionType decisionType;

    @Column(name = "current_price")
    private BigDecimal currentPrice;

    @Column(name = "target_price")
    private BigDecimal targetPrice;

    @Column(name = "price_change_amount")
    private BigDecimal priceChangeAmount;

    @Column(name = "price_change_pct")
    private BigDecimal priceChangePct;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_type", nullable = false, length = 30)
    private PolicyType strategyType;

    @Column(name = "strategy_raw_price")
    private BigDecimal strategyRawPrice;

    @Column(name = "signal_snapshot", columnDefinition = "jsonb")
    private String signalSnapshot;

    @Column(name = "constraints_applied", columnDefinition = "jsonb")
    private String constraintsApplied;

    @Column(name = "guards_evaluated", columnDefinition = "jsonb")
    private String guardsEvaluated;

    @Column(name = "skip_reason")
    private String skipReason;

    @Column(name = "explanation_summary")
    private String explanationSummary;

    @Column(name = "execution_mode", nullable = false, length = 20)
    private String executionMode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
