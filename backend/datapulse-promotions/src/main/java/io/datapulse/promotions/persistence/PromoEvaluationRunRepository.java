package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.PromoRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromoEvaluationRunRepository extends JpaRepository<PromoEvaluationRunEntity, Long> {

    Page<PromoEvaluationRunEntity> findAllByWorkspaceId(Long workspaceId, Pageable pageable);

    Page<PromoEvaluationRunEntity> findAllByWorkspaceIdAndConnectionId(
            Long workspaceId, Long connectionId, Pageable pageable);

    Page<PromoEvaluationRunEntity> findAllByWorkspaceIdAndStatus(
            Long workspaceId, PromoRunStatus status, Pageable pageable);

    Optional<PromoEvaluationRunEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsBySourceJobExecutionId(Long sourceJobExecutionId);

    boolean existsByConnectionIdAndStatus(Long connectionId, PromoRunStatus status);
}
