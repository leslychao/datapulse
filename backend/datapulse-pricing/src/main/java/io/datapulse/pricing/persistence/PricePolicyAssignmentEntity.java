package io.datapulse.pricing.persistence;

import io.datapulse.platform.persistence.BaseEntity;
import io.datapulse.pricing.domain.ScopeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "price_policy_assignment")
public class PricePolicyAssignmentEntity extends BaseEntity {

    @Column(name = "price_policy_id", nullable = false)
    private Long pricePolicyId;

    @Column(name = "marketplace_connection_id", nullable = false)
    private Long marketplaceConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private ScopeType scopeType;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "marketplace_offer_id")
    private Long marketplaceOfferId;
}
