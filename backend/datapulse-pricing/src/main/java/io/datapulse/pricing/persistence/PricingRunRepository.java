package io.datapulse.pricing.persistence;

import io.datapulse.pricing.domain.RunStatus;
import io.datapulse.pricing.domain.RunTriggerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PricingRunRepository extends JpaRepository<PricingRunEntity, Long> {

    Page<PricingRunEntity> findAllByWorkspaceId(Long workspaceId, Pageable pageable);

    Page<PricingRunEntity> findAllByWorkspaceIdAndConnectionId(
            Long workspaceId, Long connectionId, Pageable pageable);

    Optional<PricingRunEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByConnectionIdAndStatus(Long connectionId, RunStatus status);

    boolean existsBySourceJobExecutionId(Long sourceJobExecutionId);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN TRUE ELSE FALSE END
            FROM PricingRunEntity r
            WHERE r.requestHash = :hash
              AND r.triggerType = :triggerType
              AND r.status NOT IN :terminalStatuses
            """)
    boolean existsByRequestHashAndTriggerTypeAndStatusNotIn(
            @Param("hash") String requestHash,
            @Param("triggerType") RunTriggerType triggerType,
            @Param("terminalStatuses") List<RunStatus> terminalStatuses);
}
