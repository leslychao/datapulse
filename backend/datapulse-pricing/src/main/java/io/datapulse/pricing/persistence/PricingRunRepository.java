package io.datapulse.pricing.persistence;

import io.datapulse.pricing.domain.RunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PricingRunRepository extends JpaRepository<PricingRunEntity, Long> {

    Page<PricingRunEntity> findAllByWorkspaceId(Long workspaceId, Pageable pageable);

    Page<PricingRunEntity> findAllByWorkspaceIdAndConnectionId(
            Long workspaceId, Long connectionId, Pageable pageable);

    Optional<PricingRunEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByConnectionIdAndStatus(Long connectionId, RunStatus status);

    boolean existsBySourceJobExecutionId(Long sourceJobExecutionId);
}
