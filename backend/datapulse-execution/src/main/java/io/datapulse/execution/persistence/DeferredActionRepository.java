package io.datapulse.execution.persistence;

import io.datapulse.execution.domain.ActionExecutionMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface DeferredActionRepository extends JpaRepository<DeferredActionEntity, Long> {

    Optional<DeferredActionEntity> findByMarketplaceOfferIdAndExecutionMode(
            long marketplaceOfferId, ActionExecutionMode executionMode);

    @Query("""
            SELECT da FROM DeferredActionEntity da
            WHERE da.expiresAt > :now
              AND NOT EXISTS (
                SELECT 1 FROM PriceActionEntity pa
                WHERE pa.marketplaceOfferId = da.marketplaceOfferId
                  AND pa.executionMode = da.executionMode
                  AND pa.status NOT IN (
                    io.datapulse.execution.domain.ActionStatus.SUCCEEDED,
                    io.datapulse.execution.domain.ActionStatus.FAILED,
                    io.datapulse.execution.domain.ActionStatus.EXPIRED,
                    io.datapulse.execution.domain.ActionStatus.CANCELLED,
                    io.datapulse.execution.domain.ActionStatus.SUPERSEDED
                  )
              )
            """)
    List<DeferredActionEntity> findReadyToExecute(@Param("now") OffsetDateTime now);

    @Modifying
    @Query("""
            DELETE FROM DeferredActionEntity da
            WHERE da.expiresAt <= :now
            """)
    int deleteExpired(@Param("now") OffsetDateTime now);
}
