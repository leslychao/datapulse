package io.datapulse.etl.persistence.canonical;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "canonical_promo_product")
public class CanonicalPromoProductEntity extends BaseEntity {

    @Column(name = "canonical_promo_campaign_id", nullable = false)
    private Long canonicalPromoCampaignId;

    @Column(name = "marketplace_offer_id", nullable = false)
    private Long marketplaceOfferId;

    @Column(name = "participation_status", nullable = false, length = 30)
    private String participationStatus;

    @Column(name = "required_price")
    private BigDecimal requiredPrice;

    @Column(name = "current_price")
    private BigDecimal currentPrice;

    @Column(name = "max_promo_price")
    private BigDecimal maxPromoPrice;

    @Column(name = "max_discount_pct")
    private BigDecimal maxDiscountPct;

    @Column(name = "min_stock_required")
    private Integer minStockRequired;

    @Column(name = "stock_available")
    private Integer stockAvailable;

    @Column(name = "add_mode", length = 60)
    private String addMode;

    @Column(name = "participation_decision_source", length = 20)
    private String participationDecisionSource;

    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;

    @Column(name = "synced_at")
    private OffsetDateTime syncedAt;
}
