package io.datapulse.execution.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SimulatedOfferStateRepository extends JpaRepository<SimulatedOfferStateEntity, Long> {

    Optional<SimulatedOfferStateEntity> findByWorkspaceIdAndMarketplaceOfferId(
            long workspaceId, long marketplaceOfferId);

    @Modifying
    @Query("""
            DELETE FROM SimulatedOfferStateEntity s
            WHERE s.workspaceId = :workspaceId
              AND s.marketplaceOfferId IN (
                SELECT mo.id FROM MarketplaceOfferEntity mo
                WHERE mo.marketplaceConnectionId = :connectionId
              )
            """)
    int deleteByConnection(@Param("workspaceId") long workspaceId,
                           @Param("connectionId") long connectionId);
}
