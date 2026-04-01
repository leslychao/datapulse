package io.datapulse.execution.persistence;

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
@Table(name = "simulated_offer_state")
public class SimulatedOfferStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "marketplace_offer_id", nullable = false)
    private Long marketplaceOfferId;

    @Column(name = "simulated_price", nullable = false)
    private BigDecimal simulatedPrice;

    @Column(name = "simulated_at", nullable = false)
    private OffsetDateTime simulatedAt;

    @Column(name = "price_action_id", nullable = false)
    private Long priceActionId;

    @Column(name = "previous_simulated_price")
    private BigDecimal previousSimulatedPrice;

    @Column(name = "canonical_price_at_simulation", nullable = false)
    private BigDecimal canonicalPriceAtSimulation;

    @Column(name = "price_delta")
    private BigDecimal priceDelta;

    @Column(name = "price_delta_pct")
    private BigDecimal priceDeltaPct;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
