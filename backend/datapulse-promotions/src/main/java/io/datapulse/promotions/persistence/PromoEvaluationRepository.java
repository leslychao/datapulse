package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.PromoEvaluationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromoEvaluationRepository extends JpaRepository<PromoEvaluationEntity, Long> {

    Page<PromoEvaluationEntity> findAllByPromoEvaluationRunId(Long runId, Pageable pageable);

    Page<PromoEvaluationEntity> findAllByWorkspaceIdAndEvaluationResult(
            Long workspaceId, PromoEvaluationResult result, Pageable pageable);

    Page<PromoEvaluationEntity> findAllByWorkspaceId(Long workspaceId, Pageable pageable);

    Optional<PromoEvaluationEntity> findFirstByCanonicalPromoProductIdOrderByCreatedAtDesc(
            Long canonicalPromoProductId);
}
