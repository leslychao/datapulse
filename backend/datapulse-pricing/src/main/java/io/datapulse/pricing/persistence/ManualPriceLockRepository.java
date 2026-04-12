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
              AND l.workspaceId = :workspaceId
              AND l.unlockedAt IS NULL
              AND (l.expiresAt IS NULL OR l.expiresAt > CURRENT_TIMESTAMP)
            """)
    Optional<ManualPriceLockEntity> findActiveLock(
        @Param("offerId") Long marketplaceOfferId,
        @Param("workspaceId") Long workspaceId);

    @Query("""
            SELECT CASE WHEN COUNT(l) > 0 THEN TRUE ELSE FALSE END
            FROM ManualPriceLockEntity l
            WHERE l.marketplaceOfferId = :offerId
              AND l.workspaceId = :workspaceId
              AND l.unlockedAt IS NULL
              AND (l.expiresAt IS NULL OR l.expiresAt > CURRENT_TIMESTAMP)
            """)
    boolean isLocked(
        @Param("offerId") Long marketplaceOfferId,
        @Param("workspaceId") Long workspaceId);

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

    @Query(value = """
            SELECT l.* FROM manual_price_lock l
            JOIN marketplace_offer o ON o.id = l.marketplace_offer_id
            JOIN marketplace_connection mc ON mc.id = o.marketplace_connection_id
            WHERE l.workspace_id = :workspaceId
              AND l.unlocked_at IS NULL
              AND (:sourcePlatform IS NULL OR mc.marketplace_type = :sourcePlatform)
              AND (:search IS NULL
                   OR LOWER(o.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(o.marketplace_sku) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY l.locked_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM manual_price_lock l
            JOIN marketplace_offer o ON o.id = l.marketplace_offer_id
            JOIN marketplace_connection mc ON mc.id = o.marketplace_connection_id
            WHERE l.workspace_id = :workspaceId
              AND l.unlocked_at IS NULL
              AND (:sourcePlatform IS NULL OR mc.marketplace_type = :sourcePlatform)
              AND (:search IS NULL
                   OR LOWER(o.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(o.marketplace_sku) LIKE LOWER(CONCAT('%', :search, '%')))
            """,
            nativeQuery = true)
    Page<ManualPriceLockEntity> findActiveLocksFiltered(
        @Param("workspaceId") Long workspaceId,
        @Param("sourcePlatform") String sourcePlatform,
        @Param("search") String search,
        Pageable pageable);
}
