package io.datapulse.pricing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "manual_price_lock")
public class ManualPriceLockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "marketplace_offer_id", nullable = false)
    private Long marketplaceOfferId;

    @Column(name = "locked_price", nullable = false)
    private BigDecimal lockedPrice;

    @Column(name = "reason")
    private String reason;

    @Column(name = "locked_by", nullable = false)
    private Long lockedBy;

    @Column(name = "locked_at", nullable = false)
    private OffsetDateTime lockedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "unlocked_at")
    private OffsetDateTime unlockedAt;

    @Column(name = "unlocked_by")
    private Long unlockedBy;

    @PrePersist
    protected void onCreate() {
        if (this.lockedAt == null) {
            this.lockedAt = OffsetDateTime.now();
        }
    }
}
