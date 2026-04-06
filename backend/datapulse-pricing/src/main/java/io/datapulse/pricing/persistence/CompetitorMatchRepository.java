package io.datapulse.pricing.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetitorMatchRepository extends JpaRepository<CompetitorMatchEntity, Long> {

    List<CompetitorMatchEntity> findAllByWorkspaceId(long workspaceId);

    List<CompetitorMatchEntity> findAllByWorkspaceIdAndMarketplaceOfferId(
            long workspaceId, long marketplaceOfferId);

    Optional<CompetitorMatchEntity> findByIdAndWorkspaceId(long id, long workspaceId);

    List<CompetitorMatchEntity> findAllByMarketplaceOfferIdInAndTrustLevelIn(
            List<Long> offerIds, List<String> trustLevels);
}
