package io.datapulse.integration.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketplaceSyncStateRepository extends JpaRepository<MarketplaceSyncStateEntity, Long> {

    List<MarketplaceSyncStateEntity> findAllByMarketplaceConnectionId(Long marketplaceConnectionId);
}
