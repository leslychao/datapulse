package io.datapulse.tenancy.persistence;

import io.datapulse.tenancy.domain.InvitationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitationEntity, Long> {

    @EntityGraph(attributePaths = {"workspace"})
    Optional<WorkspaceInvitationEntity> findByTokenHash(String tokenHash);

    List<WorkspaceInvitationEntity> findByWorkspace_IdOrderByCreatedAtDesc(Long workspaceId);

    Optional<WorkspaceInvitationEntity> findByWorkspace_IdAndEmailAndStatus(
            Long workspaceId, String email, InvitationStatus status);

    Optional<WorkspaceInvitationEntity> findByIdAndWorkspace_Id(Long id, Long workspaceId);

    @Modifying
    @Query("""
            UPDATE WorkspaceInvitationEntity i
            SET i.status = :newStatus
            WHERE i.status = :currentStatus AND i.expiresAt < :now
            """)
    int expirePendingInvitations(@Param("currentStatus") InvitationStatus currentStatus,
                                 @Param("newStatus") InvitationStatus newStatus,
                                 @Param("now") OffsetDateTime now);
}
