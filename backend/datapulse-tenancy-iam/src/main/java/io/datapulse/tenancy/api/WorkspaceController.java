package io.datapulse.tenancy.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.tenancy.domain.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private final WorkspaceService workspaceService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public List<WorkspaceListResponse> listWorkspaces() {
        return workspaceService.listWorkspaces(workspaceContext.getUserId());
    }

    @GetMapping("/{workspaceId}")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public WorkspaceResponse getWorkspace(@PathVariable("workspaceId") Long workspaceId) {
        return workspaceService.getWorkspace(workspaceId);
    }

    @PutMapping("/{workspaceId}")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public WorkspaceResponse updateWorkspace(@PathVariable("workspaceId") Long workspaceId,
                                             @Valid @RequestBody UpdateWorkspaceRequest request) {
        return workspaceService.updateWorkspace(workspaceId, request);
    }
}
