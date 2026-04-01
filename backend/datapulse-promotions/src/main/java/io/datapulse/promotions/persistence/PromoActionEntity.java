package io.datapulse.promotions.persistence;

import io.datapulse.platform.persistence.BaseEntity;
import io.datapulse.promotions.domain.PromoActionStatus;
import io.datapulse.promotions.domain.PromoActionType;
import io.datapulse.promotions.domain.PromoExecutionMode;
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
@Table(name = "promo_action")
public class PromoActionEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "promo_decision_id", nullable = false)
    private Long promoDecisionId;

    @Column(name = "canonical_promo_campaign_id", nullable = false)
    private Long canonicalPromoCampaignId;

    @Column(name = "marketplace_offer_id", nullable = false)
    private Long marketplaceOfferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private PromoActionType actionType;

    @Column(name = "target_promo_price")
    private BigDecimal targetPromoPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromoActionStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error")
    private String lastError;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 20)
    private PromoExecutionMode executionMode;

    @Column(name = "freeze_at_snapshot")
    private OffsetDateTime freezeAtSnapshot;

    @Column(name = "cancel_reason")
    private String cancelReason;
}
