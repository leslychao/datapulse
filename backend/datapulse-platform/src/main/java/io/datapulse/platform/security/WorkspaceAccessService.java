package io.datapulse.platform.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Access-checking bean for SpEL expressions in @PreAuthorize.
 * Validates that the requested workspace matches the authenticated context,
 * preventing IDOR attacks where path variable differs from X-Workspace-Id header.
 */
@Service("workspaceAccessService")
@RequiredArgsConstructor
public class WorkspaceAccessService {

    private final WorkspaceContext workspaceContext;

    public boolean isCurrentWorkspace(Long workspaceId) {
        return workspaceContext.getWorkspaceId() != null
                && Objects.equals(workspaceContext.getWorkspaceId(), workspaceId);
    }

    public boolean hasRole(String... roles) {
        if (workspaceContext.getRole() == null) {
            return false;
        }
        for (String role : roles) {
            if (workspaceContext.getRole().equals(role)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAdminOrOwner() {
        return hasRole("ADMIN", "OWNER");
    }

    public boolean isOwner() {
        return hasRole("OWNER");
    }
}
