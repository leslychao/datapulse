package io.datapulse.bidding.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ManualBidLockRepository extends JpaRepository<ManualBidLockEntity, Long> {

  @Modifying
  @Query("""
      DELETE FROM ManualBidLockEntity l
      WHERE l.expiresAt IS NOT NULL
        AND l.expiresAt <= CURRENT_TIMESTAMP
      """)
  int deleteExpiredLocks();

  @Query("""
      SELECT l FROM ManualBidLockEntity l
      WHERE l.workspaceId = :workspaceId
        AND l.marketplaceOfferId = :offerId
        AND (l.expiresAt IS NULL OR l.expiresAt > CURRENT_TIMESTAMP)
      """)
  Optional<ManualBidLockEntity> findByWorkspaceIdAndMarketplaceOfferId(
      @Param("workspaceId") Long workspaceId,
      @Param("offerId") Long marketplaceOfferId);

  void deleteByWorkspaceIdAndMarketplaceOfferId(Long workspaceId, Long marketplaceOfferId);
}
