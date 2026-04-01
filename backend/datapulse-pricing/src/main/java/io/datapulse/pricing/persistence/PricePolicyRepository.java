package io.datapulse.pricing.persistence;

import io.datapulse.pricing.domain.PolicyStatus;
import io.datapulse.pricing.domain.PolicyType;
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

    Optional<PricePolicyEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
