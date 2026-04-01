package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.PromoActionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromoActionRepository extends JpaRepository<PromoActionEntity, Long> {

    Page<PromoActionEntity> findAllByWorkspaceId(Long workspaceId, Pageable pageable);

    Page<PromoActionEntity> findAllByWorkspaceIdAndStatus(
            Long workspaceId, PromoActionStatus status, Pageable pageable);

    Page<PromoActionEntity> findAllByWorkspaceIdAndCanonicalPromoCampaignId(
            Long workspaceId, Long campaignId, Pageable pageable);

    Optional<PromoActionEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);

    List<PromoActionEntity> findAllByIdInAndWorkspaceId(List<Long> ids, Long workspaceId);

    @Modifying
    @Query("""
            UPDATE PromoActionEntity a SET a.status = :newStatus, a.updatedAt = CURRENT_TIMESTAMP
            WHERE a.id = :id AND a.status = :expectedStatus
            """)
    int casUpdateStatus(@Param("id") Long id,
                        @Param("expectedStatus") PromoActionStatus expectedStatus,
                        @Param("newStatus") PromoActionStatus newStatus);

    @Query("""
            SELECT a FROM PromoActionEntity a
            WHERE a.canonicalPromoCampaignId = :campaignId
              AND a.status IN ('PENDING_APPROVAL', 'APPROVED')
            """)
    List<PromoActionEntity> findPendingActionsByCampaignId(@Param("campaignId") Long campaignId);
}
