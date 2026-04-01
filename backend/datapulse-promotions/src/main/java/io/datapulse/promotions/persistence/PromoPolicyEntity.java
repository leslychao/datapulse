package io.datapulse.promotions.persistence;

import io.datapulse.platform.persistence.BaseEntity;
import io.datapulse.promotions.domain.ParticipationMode;
import io.datapulse.promotions.domain.PromoPolicyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "promo_policy")
public class PromoPolicyEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromoPolicyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_mode", nullable = false, length = 20)
    private ParticipationMode participationMode;

    @Column(name = "min_margin_pct", nullable = false)
    private BigDecimal minMarginPct;

    @Column(name = "min_stock_days_of_cover", nullable = false)
    private Integer minStockDaysOfCover;

    @Column(name = "max_promo_discount_pct")
    private BigDecimal maxPromoDiscountPct;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auto_participate_categories", columnDefinition = "jsonb")
    private String autoParticipateCategories;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auto_decline_categories", columnDefinition = "jsonb")
    private String autoDeclineCategories;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evaluation_config", columnDefinition = "jsonb")
    private String evaluationConfig;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}
