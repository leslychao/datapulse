package io.datapulse.integration.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface MarketplaceSyncStateRepository extends JpaRepository<MarketplaceSyncStateEntity, Long> {

    List<MarketplaceSyncStateEntity> findAllByMarketplaceConnectionId(Long marketplaceConnectionId);

    @Query("""
            SELECT s FROM MarketplaceSyncStateEntity s
            WHERE s.nextScheduledAt <= :now
              AND s.status <> 'DISABLED'
            """)
    List<MarketplaceSyncStateEntity> findEligibleForSync(@Param("now") OffsetDateTime now);

    @Query("""
            SELECT DISTINCT s.marketplaceConnectionId FROM MarketplaceSyncStateEntity s
            WHERE s.status = 'SYNCING'
            """)
    List<Long> findDistinctMarketplaceConnectionIdsWithSyncingStatus();
}
