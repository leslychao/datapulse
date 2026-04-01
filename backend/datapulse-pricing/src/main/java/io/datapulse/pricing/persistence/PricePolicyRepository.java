package io.datapulse.pricing.persistence;

import io.datapulse.pricing.domain.PolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PricePolicyRepository extends JpaRepository<PricePolicyEntity, Long> {

    List<PricePolicyEntity> findAllByWorkspaceId(Long workspaceId);

    List<PricePolicyEntity> findAllByWorkspaceIdAndStatus(Long workspaceId, PolicyStatus status);

    Optional<PricePolicyEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
