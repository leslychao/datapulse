package io.datapulse.pricing.persistence;

import io.datapulse.pricing.domain.PolicyStatus;
import io.datapulse.pricing.domain.PolicyType;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PricePolicyRepository extends JpaRepository<PricePolicyEntity, Long> {

    List<PricePolicyEntity> findAllByWorkspaceId(Long workspaceId);

    List<PricePolicyEntity> findAllByWorkspaceIdAndStatus(Long workspaceId, PolicyStatus status);

    List<PricePolicyEntity> findAllByWorkspaceIdAndStrategyType(
            Long workspaceId, PolicyType strategyType);

    List<PricePolicyEntity> findAllByWorkspaceIdAndStatusAndStrategyType(
            Long workspaceId, PolicyStatus status, PolicyType strategyType);

    Page<PricePolicyEntity> findAllByWorkspaceId(Long workspaceId, Pageable pageable);

    Page<PricePolicyEntity> findAllByWorkspaceIdAndStatusIn(
            Long workspaceId, Collection<PolicyStatus> statuses, Pageable pageable);

    Page<PricePolicyEntity> findAllByWorkspaceIdAndStrategyType(
            Long workspaceId, PolicyType strategyType, Pageable pageable);

    Page<PricePolicyEntity> findAllByWorkspaceIdAndStatusInAndStrategyType(
            Long workspaceId, Collection<PolicyStatus> statuses,
            PolicyType strategyType, Pageable pageable);

    Optional<PricePolicyEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query("""
        SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
        FROM PricePolicyAssignmentEntity a
        WHERE a.pricePolicyId = :policyId AND a.scopeType = 'CONNECTION'
        """)
    boolean existsConnectionScopeAssignment(@Param("policyId") Long policyId);
}
