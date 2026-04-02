package io.datapulse.tenancy.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.tenancy.persistence.AppUserEntity;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.WorkspaceEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AppUserRepository appUserRepository;
    private final TenancyAuditPublisher auditPublisher;

    @Transactional(readOnly = true)
    public List<WorkspaceMemberEntity> listMembers(Long workspaceId) {
        return memberRepository.findByWorkspace_IdAndStatus(workspaceId, MemberStatus.ACTIVE);
    }

    @Transactional
    public WorkspaceMemberEntity changeRole(Long workspaceId, Long targetUserId,
                                            Long actorUserId, String actorRole,
                                            MemberRole newRole) {
        WorkspaceMemberEntity member = memberRepository
                .findByWorkspace_IdAndUser_IdAndStatus(workspaceId, targetUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> NotFoundException.entity("WorkspaceMember", targetUserId));

        if (member.getRole() == MemberRole.OWNER) {
            throw BadRequestException.of("member.role.cannot.change.owner");
        }
        if (targetUserId.equals(actorUserId)) {
            throw BadRequestException.of("member.role.cannot.change.self");
        }
        if (newRole == MemberRole.OWNER) {
            throw BadRequestException.of("member.role.cannot.assign.owner");
        }

        MemberRole actorMemberRole = MemberRole.valueOf(actorRole);
        if (actorMemberRole == MemberRole.ADMIN && newRole == MemberRole.ADMIN) {
            throw BadRequestException.of("member.role.admin.cannot.assign.admin");
        }

        MemberRole oldRole = member.getRole();
        member.setRole(newRole);
        memberRepository.save(member);
        auditPublisher.publish("member.change_role", "workspace_member",
                String.valueOf(targetUserId),
                "{\"old_role\":\"%s\",\"new_role\":\"%s\"}".formatted(oldRole.name(), newRole.name()));
        return member;
    }

    @Transactional
    public void removeMember(Long workspaceId, Long targetUserId, Long actorUserId) {
        WorkspaceMemberEntity member = memberRepository
                .findByWorkspace_IdAndUser_IdAndStatus(workspaceId, targetUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> NotFoundException.entity("WorkspaceMember", targetUserId));

        if (member.getRole() == MemberRole.OWNER) {
            throw BadRequestException.of("member.cannot.remove.owner");
        }
        if (targetUserId.equals(actorUserId)) {
            throw BadRequestException.of("member.cannot.remove.self");
        }

        member.setStatus(MemberStatus.INACTIVE);
        memberRepository.save(member);
        auditPublisher.publish("member.remove", "workspace_member",
                String.valueOf(targetUserId));
    }

    @Transactional
    public void transferOwnership(Long workspaceId, Long currentOwnerId, Long newOwnerUserId) {
        if (currentOwnerId.equals(newOwnerUserId)) {
            throw BadRequestException.of("member.transfer.self");
        }

        WorkspaceMemberEntity currentOwnerMember = memberRepository
                .findByWorkspace_IdAndUser_IdAndStatus(workspaceId, currentOwnerId, MemberStatus.ACTIVE)
                .orElseThrow(() -> NotFoundException.entity("WorkspaceMember", currentOwnerId));

        if (currentOwnerMember.getRole() != MemberRole.OWNER) {
            throw BadRequestException.of("member.role.cannot.change.owner");
        }

        WorkspaceMemberEntity newOwnerMember = memberRepository
                .findByWorkspace_IdAndUser_IdAndStatus(workspaceId, newOwnerUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> NotFoundException.of("member.transfer.target.not.found"));

        currentOwnerMember.setRole(MemberRole.ADMIN);
        newOwnerMember.setRole(MemberRole.OWNER);
        memberRepository.save(currentOwnerMember);
        memberRepository.save(newOwnerMember);

        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> NotFoundException.workspace(workspaceId));
        workspace.setOwnerUserId(newOwnerUserId);
        workspaceRepository.save(workspace);

        auditPublisher.publish("workspace.transfer_ownership", "workspace",
                String.valueOf(workspaceId),
                "{\"from\":%d,\"to\":%d}".formatted(currentOwnerId, newOwnerUserId));
    }

    @Transactional
    public void deactivateUser(Long userId) {
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.entity("AppUser", userId));

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw BadRequestException.of("user.already.deactivated");
        }

        user.setStatus(UserStatus.DEACTIVATED);
        appUserRepository.save(user);

        List<WorkspaceMemberEntity> activeMemberships = memberRepository
                .findByUser_IdAndStatus(userId, MemberStatus.ACTIVE);
        for (WorkspaceMemberEntity membership : activeMemberships) {
            membership.setStatus(MemberStatus.INACTIVE);
            memberRepository.save(membership);
        }

        auditPublisher.publish("user.deactivate", "app_user", String.valueOf(userId));
    }

    @Transactional
    public void reactivateUser(Long userId) {
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.entity("AppUser", userId));

        if (user.getStatus() != UserStatus.DEACTIVATED) {
            throw BadRequestException.of("user.not.deactivated");
        }

        user.setStatus(UserStatus.ACTIVE);
        appUserRepository.save(user);

        List<WorkspaceMemberEntity> inactiveMemberships = memberRepository
                .findByUser_IdAndStatus(userId, MemberStatus.INACTIVE);
        for (WorkspaceMemberEntity membership : inactiveMemberships) {
            membership.setStatus(MemberStatus.ACTIVE);
            memberRepository.save(membership);
        }

        auditPublisher.publish("user.reactivate", "app_user", String.valueOf(userId));
    }
}
