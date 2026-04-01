package io.datapulse.pricing.persistence;

import io.datapulse.pricing.domain.DecisionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PriceDecisionRepository extends JpaRepository<PriceDecisionEntity, Long> {

    Page<PriceDecisionEntity> findAllByWorkspaceId(Long workspaceId, Pageable pageable);

    Page<PriceDecisionEntity> findAllByPricingRunId(Long pricingRunId, Pageable pageable);

    Optional<PriceDecisionEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query("""
            SELECT d FROM PriceDecisionEntity d
            WHERE d.marketplaceOfferId = :offerId
            ORDER BY d.createdAt DESC
            """)
    List<PriceDecisionEntity> findLatestByOffer(
            @Param("offerId") Long marketplaceOfferId, Pageable pageable);

    @Query("""
            SELECT d FROM PriceDecisionEntity d
            WHERE d.marketplaceOfferId = :offerId
              AND d.decisionType = :decisionType
              AND d.createdAt >= :since
            ORDER BY d.createdAt DESC
            """)
    List<PriceDecisionEntity> findRecentChanges(
            @Param("offerId") Long marketplaceOfferId,
            @Param("decisionType") DecisionType decisionType,
            @Param("since") OffsetDateTime since);
}
