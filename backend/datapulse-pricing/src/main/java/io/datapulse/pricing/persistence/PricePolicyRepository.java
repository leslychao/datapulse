package io.datapulse.pricing.persistence;

import io.datapulse.pricing.domain.PolicyStatus;
import io.datapulse.pricing.domain.PolicyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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

    Page<PricePolicyEntity> findAllByWorkspaceIdAndStatus(
            Long workspaceId, PolicyStatus status, Pageable pageable);

    Page<PricePolicyEntity> findAllByWorkspaceIdAndStrategyType(
            Long workspaceId, PolicyType strategyType, Pageable pageable);

    Page<PricePolicyEntity> findAllByWorkspaceIdAndStatusAndStrategyType(
            Long workspaceId, PolicyStatus status, PolicyType strategyType, Pageable pageable);

    Optional<PricePolicyEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
