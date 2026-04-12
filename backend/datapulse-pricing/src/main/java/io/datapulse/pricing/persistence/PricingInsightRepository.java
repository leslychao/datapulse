package io.datapulse.pricing.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PricingInsightRepository extends JpaRepository<PricingInsightEntity, Long> {

  Page<PricingInsightEntity> findAllByWorkspaceIdOrderByCreatedAtDesc(
      Long workspaceId, Pageable pageable);

  @Query("""
      SELECT i FROM PricingInsightEntity i
      WHERE i.workspaceId = :workspaceId
        AND i.acknowledged = false
      ORDER BY i.createdAt DESC
      """)
  Page<PricingInsightEntity> findUnacknowledged(
      @Param("workspaceId") Long workspaceId, Pageable pageable);

  @Query("""
      SELECT i FROM PricingInsightEntity i
      WHERE i.workspaceId = :workspaceId
        AND i.insightType = :insightType
      ORDER BY i.createdAt DESC
      """)
  Page<PricingInsightEntity> findByType(
      @Param("workspaceId") Long workspaceId,
      @Param("insightType") String insightType,
      Pageable pageable);

  @Query("""
      SELECT i FROM PricingInsightEntity i
      WHERE i.workspaceId = :workspaceId
        AND i.insightType = :insightType
        AND i.acknowledged = :acknowledged
      ORDER BY i.createdAt DESC
      """)
  Page<PricingInsightEntity> findByTypeAndAcknowledged(
      @Param("workspaceId") Long workspaceId,
      @Param("insightType") String insightType,
      @Param("acknowledged") boolean acknowledged,
      Pageable pageable);

  @Query("""
      SELECT i FROM PricingInsightEntity i
      WHERE i.workspaceId = :workspaceId
        AND i.acknowledged = :acknowledged
      ORDER BY i.createdAt DESC
      """)
  Page<PricingInsightEntity> findByAcknowledged(
      @Param("workspaceId") Long workspaceId,
      @Param("acknowledged") boolean acknowledged,
      Pageable pageable);

  Optional<PricingInsightEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);

  long countByWorkspaceIdAndAcknowledgedFalse(Long workspaceId);
}
