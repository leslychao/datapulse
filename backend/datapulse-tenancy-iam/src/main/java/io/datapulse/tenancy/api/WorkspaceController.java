package io.datapulse.tenancy.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.tenancy.domain.MemberService;
import io.datapulse.tenancy.domain.WorkspaceService;
import io.datapulse.tenancy.domain.WorkspaceSummary;
import io.datapulse.tenancy.persistence.WorkspaceEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/workspaces", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final MemberService memberService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<WorkspaceListResponse> listWorkspaces() {
        return workspaceService.listWorkspaces(workspaceContext.getUserId()).stream()
                .map(this::toListResponse)
                .toList();
    }

    @GetMapping("/{workspaceId}")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public WorkspaceResponse getWorkspace(@PathVariable("workspaceId") Long workspaceId) {
        WorkspaceEntity ws = workspaceService.getWorkspace(workspaceId);
        return toResponse(ws);
    }

    @PutMapping("/{workspaceId}")
    @PreAuthorize(
            "@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
                    + " and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public WorkspaceResponse updateWorkspace(@PathVariable("workspaceId") Long workspaceId,
                                             @Valid @RequestBody UpdateWorkspaceRequest request) {
        WorkspaceEntity ws = workspaceService.updateWorkspace(workspaceId, request.name());
        return toResponse(ws);
    }

    @PostMapping("/{workspaceId}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(
            "@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
                    + " and hasAnyAuthority('ROLE_OWNER')")
    public void suspendWorkspace(@PathVariable("workspaceId") Long workspaceId) {
        workspaceService.suspendWorkspace(workspaceId);
    }

    @PostMapping("/{workspaceId}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(
            "@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
                    + " and hasAnyAuthority('ROLE_OWNER')")
    public void reactivateWorkspace(@PathVariable("workspaceId") Long workspaceId) {
        workspaceService.reactivateWorkspace(workspaceId);
    }

    @PostMapping("/{workspaceId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(
            "@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
                    + " and hasAnyAuthority('ROLE_OWNER')")
    public void archiveWorkspace(@PathVariable("workspaceId") Long workspaceId) {
        workspaceService.archiveWorkspace(workspaceId);
    }

    @PostMapping("/{workspaceId}/ownership-transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(
            "@workspaceAccessService.isCurrentWorkspace(#workspaceId)"
                    + " and hasAnyAuthority('ROLE_OWNER')")
    public void transferOwnership(@PathVariable("workspaceId") Long workspaceId,
                                  @Valid @RequestBody OwnershipTransferRequest request) {
        memberService.transferOwnership(
                workspaceId, workspaceContext.getUserId(), request.newOwnerUserId());
    }

    private WorkspaceResponse toResponse(WorkspaceEntity ws) {
        return new WorkspaceResponse(
                ws.getId(), ws.getName(), ws.getSlug(), ws.getStatus(),
                ws.getCreatedAt(),
                ws.getTenant().getId(), ws.getTenant().getName(),
                ws.getTenant().getSlug());
    }

    private WorkspaceListResponse toListResponse(WorkspaceSummary ws) {
        return new WorkspaceListResponse(
                ws.id(), ws.name(), ws.slug(), ws.status(),
                ws.tenantId(), ws.tenantName(),
                ws.connectionsCount(), ws.membersCount());
    }
}
