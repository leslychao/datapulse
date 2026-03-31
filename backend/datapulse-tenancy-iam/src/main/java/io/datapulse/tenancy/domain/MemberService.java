package io.datapulse.tenancy.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.tenancy.api.MemberResponse;
import io.datapulse.tenancy.api.UpdateMemberRoleRequest;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final WorkspaceMemberRepository memberRepository;
    private final TenancyAuditPublisher auditPublisher;

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long workspaceId) {
        return memberRepository.findByWorkspace_IdAndStatus(workspaceId, MemberStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MemberResponse changeRole(Long workspaceId, Long targetUserId, Long actorUserId,
                                     String actorRole, UpdateMemberRoleRequest request) {
        WorkspaceMemberEntity member = memberRepository
                .findByWorkspace_IdAndUser_IdAndStatus(workspaceId, targetUserId, MemberStatus.ACTIVE)
                .orElseThrow(() -> NotFoundException.entity("WorkspaceMember", targetUserId));

        if (member.getRole() == MemberRole.OWNER) {
            throw BadRequestException.of("member.role.cannot.change.owner");
        }
        if (targetUserId.equals(actorUserId)) {
            throw BadRequestException.of("member.role.cannot.change.self");
        }
        if (request.role() == MemberRole.OWNER) {
            throw BadRequestException.of("member.role.cannot.assign.owner");
        }

        MemberRole actorMemberRole = MemberRole.valueOf(actorRole);
        if (actorMemberRole == MemberRole.ADMIN && request.role() == MemberRole.ADMIN) {
            throw BadRequestException.of("member.role.admin.cannot.assign.admin");
        }

        member.setRole(request.role());
        memberRepository.save(member);
        auditPublisher.publish("member.role.change", "workspace_member",
                String.valueOf(targetUserId));
        return toResponse(member);
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

    private MemberResponse toResponse(WorkspaceMemberEntity entity) {
        return new MemberResponse(
                entity.getUser().getId(),
                entity.getUser().getEmail(),
                entity.getUser().getName(),
                entity.getRole(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
