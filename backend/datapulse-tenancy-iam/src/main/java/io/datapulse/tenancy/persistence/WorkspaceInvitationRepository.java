package io.datapulse.tenancy.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitationEntity, Long> {

    Optional<WorkspaceInvitationEntity> findByTokenHash(String tokenHash);
}
