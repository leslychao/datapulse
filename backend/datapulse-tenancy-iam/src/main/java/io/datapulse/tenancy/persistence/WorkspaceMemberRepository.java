package io.datapulse.tenancy.persistence;

import io.datapulse.tenancy.domain.MemberRole;
import io.datapulse.tenancy.domain.MemberStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMemberEntity, Long> {

    Optional<WorkspaceMemberEntity> findByWorkspace_IdAndUser_Id(Long workspaceId, Long userId);

    @EntityGraph(attributePaths = {"user"})
    Optional<WorkspaceMemberEntity> findByWorkspace_IdAndUser_IdAndStatus(Long workspaceId,
                                                                          Long userId,
                                                                          MemberStatus status);

    @EntityGraph(attributePaths = {"workspace", "workspace.tenant"})
    List<WorkspaceMemberEntity> findByUser_IdAndStatus(Long userId, MemberStatus status);

    @EntityGraph(attributePaths = {"user"})
    List<WorkspaceMemberEntity> findByWorkspace_IdAndStatus(Long workspaceId, MemberStatus status);

    boolean existsByWorkspace_IdAndUser_IdAndStatus(Long workspaceId, Long userId, MemberStatus status);

    long countByWorkspace_IdAndStatus(Long workspaceId, MemberStatus status);

    long countByWorkspace_IdAndRoleAndStatus(Long workspaceId, MemberRole role, MemberStatus status);
}
