package io.datapulse.tenancy.persistence;

import io.datapulse.tenancy.domain.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitationEntity, Long> {

    Optional<WorkspaceInvitationEntity> findByTokenHash(String tokenHash);

    List<WorkspaceInvitationEntity> findByWorkspace_IdOrderByCreatedAtDesc(Long workspaceId);

    Optional<WorkspaceInvitationEntity> findByWorkspace_IdAndEmailAndStatus(
            Long workspaceId, String email, InvitationStatus status);

    Optional<WorkspaceInvitationEntity> findByIdAndWorkspace_Id(Long id, Long workspaceId);
}
