package io.datapulse.integration.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketplaceConnectionRepository extends JpaRepository<MarketplaceConnectionEntity, Long> {

    List<MarketplaceConnectionEntity> findAllByWorkspaceId(Long workspaceId);

    List<MarketplaceConnectionEntity> findAllByWorkspaceIdAndStatusNot(Long workspaceId, String status);

    Optional<MarketplaceConnectionEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);

    List<MarketplaceConnectionEntity> findAllByMarketplaceType(String marketplaceType);

    List<MarketplaceConnectionEntity> findAllByStatus(String status);

    boolean existsByWorkspaceIdAndMarketplaceTypeAndExternalAccountId(
            Long workspaceId, String marketplaceType, String externalAccountId);

    boolean existsByWorkspaceIdAndMarketplaceTypeAndExternalAccountIdAndIdNot(
            Long workspaceId, String marketplaceType, String externalAccountId, Long id);

    boolean existsByWorkspaceIdAndMarketplaceTypeAndExternalAccountIdAndIdNotAndStatusNot(
            Long workspaceId, String marketplaceType, String externalAccountId, Long id, String status);
}
