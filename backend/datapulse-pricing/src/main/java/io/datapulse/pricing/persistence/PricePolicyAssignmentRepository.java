package io.datapulse.pricing.persistence;

import io.datapulse.pricing.domain.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PricePolicyAssignmentRepository extends JpaRepository<PricePolicyAssignmentEntity, Long> {

    List<PricePolicyAssignmentEntity> findAllByPricePolicyId(Long pricePolicyId);

    List<PricePolicyAssignmentEntity> findAllByMarketplaceConnectionId(Long marketplaceConnectionId);

    void deleteAllByPricePolicyId(Long pricePolicyId);

    boolean existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeType(
            Long pricePolicyId, Long marketplaceConnectionId, ScopeType scopeType);

    boolean existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeTypeAndCategoryId(
            Long pricePolicyId, Long marketplaceConnectionId, ScopeType scopeType, Long categoryId);

    boolean existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeTypeAndMarketplaceOfferId(
            Long pricePolicyId, Long marketplaceConnectionId, ScopeType scopeType, Long marketplaceOfferId);

    @Query("""
        SELECT DISTINCT a.marketplaceConnectionId, p.workspaceId
        FROM PricePolicyAssignmentEntity a
        JOIN PricePolicyEntity p ON p.id = a.pricePolicyId
        WHERE p.status = 'ACTIVE'
        """)
    List<Object[]> findDistinctConnectionsWithActivePolicies();

    @Query("""
        SELECT DISTINCT a.marketplaceConnectionId
        FROM PricePolicyAssignmentEntity a
        WHERE a.pricePolicyId = :policyId
        """)
    List<Long> findDistinctConnectionIdsByPolicyId(@Param("policyId") Long policyId);
}
