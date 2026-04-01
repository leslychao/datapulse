package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.PromoEvaluationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PromoEvaluationRepository extends JpaRepository<PromoEvaluationEntity, Long> {

    Page<PromoEvaluationEntity> findAllByPromoEvaluationRunId(Long runId, Pageable pageable);

    Page<PromoEvaluationEntity> findAllByWorkspaceIdAndEvaluationResult(
            Long workspaceId, PromoEvaluationResult result, Pageable pageable);

    Page<PromoEvaluationEntity> findAllByWorkspaceId(Long workspaceId, Pageable pageable);

    @Query("""
            SELECT e FROM PromoEvaluationEntity e
            WHERE e.canonicalPromoProductId = :promoProductId
            ORDER BY e.createdAt DESC
            """)
    Optional<PromoEvaluationEntity> findLatestByPromoProductId(
            @Param("promoProductId") Long promoProductId);
}
