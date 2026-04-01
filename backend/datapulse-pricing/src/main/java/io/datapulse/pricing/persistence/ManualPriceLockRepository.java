package io.datapulse.pricing.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ManualPriceLockRepository extends JpaRepository<ManualPriceLockEntity, Long> {

    @Query("""
            SELECT l FROM ManualPriceLockEntity l
            WHERE l.marketplaceOfferId = :offerId
              AND l.unlockedAt IS NULL
              AND (l.expiresAt IS NULL OR l.expiresAt > CURRENT_TIMESTAMP)
            """)
    Optional<ManualPriceLockEntity> findActiveLock(@Param("offerId") Long marketplaceOfferId);

    @Query("""
            SELECT CASE WHEN COUNT(l) > 0 THEN TRUE ELSE FALSE END
            FROM ManualPriceLockEntity l
            WHERE l.marketplaceOfferId = :offerId
              AND l.unlockedAt IS NULL
              AND (l.expiresAt IS NULL OR l.expiresAt > CURRENT_TIMESTAMP)
            """)
    boolean isLocked(@Param("offerId") Long marketplaceOfferId);

    List<ManualPriceLockEntity> findAllByWorkspaceIdAndUnlockedAtIsNull(Long workspaceId);

    Page<ManualPriceLockEntity> findAllByWorkspaceIdAndUnlockedAtIsNull(
        Long workspaceId, Pageable pageable);

    @Query("""
            SELECT l FROM ManualPriceLockEntity l
            WHERE l.unlockedAt IS NULL
              AND l.expiresAt IS NOT NULL
              AND l.expiresAt < CURRENT_TIMESTAMP
            """)
    List<ManualPriceLockEntity> findExpiredLocks();
}
