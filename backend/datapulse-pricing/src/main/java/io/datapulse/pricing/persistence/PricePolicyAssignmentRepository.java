package io.datapulse.pricing.persistence;

import io.datapulse.pricing.domain.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricePolicyAssignmentRepository extends JpaRepository<PricePolicyAssignmentEntity, Long> {

    List<PricePolicyAssignmentEntity> findAllByPricePolicyId(Long pricePolicyId);

    List<PricePolicyAssignmentEntity> findAllByMarketplaceConnectionId(Long marketplaceConnectionId);

    void deleteAllByPricePolicyId(Long pricePolicyId);

    boolean existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeType(
            Long pricePolicyId, Long marketplaceConnectionId, ScopeType scopeType);
}
