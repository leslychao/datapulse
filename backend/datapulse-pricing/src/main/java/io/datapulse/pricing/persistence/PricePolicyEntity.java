package io.datapulse.pricing.persistence;

import io.datapulse.platform.persistence.BaseEntity;
import io.datapulse.pricing.domain.ExecutionMode;
import io.datapulse.pricing.domain.PolicyStatus;
import io.datapulse.pricing.domain.PolicyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "price_policy")
public class PricePolicyEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_type", nullable = false, length = 30)
    private PolicyType strategyType;

    @Column(name = "strategy_params", nullable = false, columnDefinition = "jsonb")
    private String strategyParams;

    @Column(name = "min_margin_pct")
    private BigDecimal minMarginPct;

    @Column(name = "max_price_change_pct")
    private BigDecimal maxPriceChangePct;

    @Column(name = "min_price")
    private BigDecimal minPrice;

    @Column(name = "max_price")
    private BigDecimal maxPrice;

    @Column(name = "guard_config", columnDefinition = "jsonb")
    private String guardConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 20)
    private ExecutionMode executionMode;

    @Column(name = "approval_timeout_hours", nullable = false)
    private Integer approvalTimeoutHours;

    @Column(nullable = false)
    private Integer priority;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}
