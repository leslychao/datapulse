package io.datapulse.tenancy.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.tenancy.api.UpdateWorkspaceRequest;
import io.datapulse.tenancy.api.WorkspaceListResponse;
import io.datapulse.tenancy.api.WorkspaceResponse;
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
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final TenancyAuditPublisher auditPublisher;

    @Transactional(readOnly = true)
    public List<WorkspaceListResponse> listWorkspaces(Long userId) {
        List<WorkspaceMemberEntity> memberships = memberRepository
                .findByUser_IdAndStatus(userId, MemberStatus.ACTIVE);

        return memberships.stream()
                .map(m -> {
                    WorkspaceEntity ws = m.getWorkspace();
                    long membersCount = memberRepository.countByWorkspace_IdAndStatus(
                            ws.getId(), MemberStatus.ACTIVE);
                    return new WorkspaceListResponse(
                            ws.getId(), ws.getName(), ws.getSlug(), ws.getStatus(),
                            ws.getTenant().getId(), ws.getTenant().getName(),
                            0, membersCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(Long workspaceId) {
        WorkspaceEntity ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> NotFoundException.workspace(workspaceId));
        return toResponse(ws);
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(Long workspaceId, UpdateWorkspaceRequest request) {
        WorkspaceEntity ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> NotFoundException.workspace(workspaceId));
        ws.setName(request.name().trim());
        workspaceRepository.save(ws);
        auditPublisher.publish("workspace.update", "workspace", String.valueOf(workspaceId));
        return toResponse(ws);
    }

    @Transactional
    public void suspendWorkspace(Long workspaceId) {
        WorkspaceEntity ws = findActiveWorkspace(workspaceId);
        ws.setStatus(WorkspaceStatus.SUSPENDED);
        workspaceRepository.save(ws);
        auditPublisher.publish("workspace.suspend", "workspace", String.valueOf(workspaceId));
    }

    @Transactional
    public void reactivateWorkspace(Long workspaceId) {
        WorkspaceEntity ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> NotFoundException.workspace(workspaceId));
        if (ws.getStatus() != WorkspaceStatus.SUSPENDED) {
            throw BadRequestException.of("workspace.not.suspended");
        }
        ws.setStatus(WorkspaceStatus.ACTIVE);
        workspaceRepository.save(ws);
        auditPublisher.publish("workspace.reactivate", "workspace", String.valueOf(workspaceId));
    }

    @Transactional
    public void archiveWorkspace(Long workspaceId) {
        WorkspaceEntity ws = findActiveWorkspace(workspaceId);
        ws.setStatus(WorkspaceStatus.ARCHIVED);
        workspaceRepository.save(ws);
        auditPublisher.publish("workspace.archive", "workspace", String.valueOf(workspaceId));
    }

    private WorkspaceEntity findActiveWorkspace(Long workspaceId) {
        WorkspaceEntity ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> NotFoundException.workspace(workspaceId));
        if (ws.getStatus() != WorkspaceStatus.ACTIVE) {
            throw BadRequestException.of("workspace.not.active");
        }
        return ws;
    }

    private WorkspaceResponse toResponse(WorkspaceEntity ws) {
        return new WorkspaceResponse(
                ws.getId(),
                ws.getName(),
                ws.getSlug(),
                ws.getStatus(),
                ws.getCreatedAt(),
                ws.getTenant().getId(),
                ws.getTenant().getName(),
                ws.getTenant().getSlug()
        );
    }
}
