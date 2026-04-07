package io.datapulse.execution.persistence;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PriceActionRepository extends JpaRepository<PriceActionEntity, Long> {

    @Query("""
            SELECT pa FROM PriceActionEntity pa
            WHERE pa.marketplaceOfferId = :offerId
              AND pa.executionMode = :mode
              AND pa.status NOT IN (
                io.datapulse.execution.domain.ActionStatus.SUCCEEDED,
                io.datapulse.execution.domain.ActionStatus.FAILED,
                io.datapulse.execution.domain.ActionStatus.EXPIRED,
                io.datapulse.execution.domain.ActionStatus.CANCELLED,
                io.datapulse.execution.domain.ActionStatus.SUPERSEDED
              )
            """)
    Optional<PriceActionEntity> findActiveByOfferAndMode(
            @Param("offerId") long offerId,
            @Param("mode") ActionExecutionMode mode
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT pa FROM PriceActionEntity pa
            WHERE pa.marketplaceOfferId = :offerId
              AND pa.executionMode = :mode
              AND pa.status NOT IN (
                io.datapulse.execution.domain.ActionStatus.SUCCEEDED,
                io.datapulse.execution.domain.ActionStatus.FAILED,
                io.datapulse.execution.domain.ActionStatus.EXPIRED,
                io.datapulse.execution.domain.ActionStatus.CANCELLED,
                io.datapulse.execution.domain.ActionStatus.SUPERSEDED
              )
            """)
    Optional<PriceActionEntity> findActiveByOfferAndModeForUpdate(
            @Param("offerId") long offerId,
            @Param("mode") ActionExecutionMode mode
    );

    @Query("""
            SELECT pa FROM PriceActionEntity pa
            WHERE pa.status = :status
              AND pa.updatedAt < :cutoff
            """)
    List<PriceActionEntity> findStuckInStatus(
            @Param("status") ActionStatus status,
            @Param("cutoff") OffsetDateTime cutoff
    );
}
