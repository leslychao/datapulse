package io.datapulse.tenancy.api;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.tenancy.domain.MemberStatus;
import io.datapulse.tenancy.persistence.WorkspaceEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/workspaces", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    @Transactional(readOnly = true)
    public List<WorkspaceListResponse> listWorkspaces() {
        List<WorkspaceMemberEntity> memberships = memberRepository
                .findByUser_IdAndStatus(workspaceContext.getUserId(), MemberStatus.ACTIVE);

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

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse getWorkspace(@PathVariable("workspaceId") Long workspaceId) {
        WorkspaceEntity ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> NotFoundException.workspace(workspaceId));
        return toResponse(ws);
    }

    @PutMapping("/{workspaceId}")
    @Transactional
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public WorkspaceResponse updateWorkspace(@PathVariable("workspaceId") Long workspaceId,
                                             @Valid @RequestBody UpdateWorkspaceRequest request) {
        WorkspaceEntity ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> NotFoundException.workspace(workspaceId));
        ws.setName(request.name().trim());
        workspaceRepository.save(ws);
        return toResponse(ws);
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
