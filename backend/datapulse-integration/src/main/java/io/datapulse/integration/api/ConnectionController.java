package io.datapulse.integration.api;

import io.datapulse.integration.domain.ConnectionService;
import io.datapulse.platform.security.WorkspaceContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping(value = "/api/connections", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;
    private final WorkspaceContext workspaceContext;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public ConnectionResponse createConnection(@Valid @RequestBody CreateConnectionRequest request) {
        return connectionService.createConnection(
                request, workspaceContext.getWorkspaceId(), workspaceContext.getUserId());
    }

    @GetMapping
    public List<ConnectionSummaryResponse> listConnections() {
        return connectionService.listConnections(workspaceContext.getWorkspaceId());
    }

    @GetMapping("/{connectionId}")
    public ConnectionResponse getConnection(@PathVariable("connectionId") Long connectionId) {
        return connectionService.getConnection(connectionId, workspaceContext.getWorkspaceId());
    }

    @PutMapping("/{connectionId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public ConnectionResponse updateConnection(@PathVariable("connectionId") Long connectionId,
                                               @Valid @RequestBody UpdateConnectionRequest request) {
        return connectionService.updateConnection(connectionId, request, workspaceContext.getWorkspaceId());
    }

    @PutMapping("/{connectionId}/credentials")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public ConnectionResponse updateCredentials(@PathVariable("connectionId") Long connectionId,
                                                @Valid @RequestBody UpdateCredentialsRequest request) {
        return connectionService.updateCredentials(
                connectionId, request, workspaceContext.getWorkspaceId(), workspaceContext.getUserId());
    }

    @PutMapping("/{connectionId}/performance-credentials")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public ConnectionResponse updatePerformanceCredentials(
            @PathVariable("connectionId") Long connectionId,
            @Valid @RequestBody UpdatePerformanceCredentialsRequest request) {
        return connectionService.updatePerformanceCredentials(
                connectionId, request, workspaceContext.getWorkspaceId(), workspaceContext.getUserId());
    }

    @PostMapping("/{connectionId}/validate")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public ValidateConnectionResponse validateConnection(@PathVariable("connectionId") Long connectionId) {
        return connectionService.validateConnection(connectionId, workspaceContext.getWorkspaceId());
    }

    @PostMapping("/{connectionId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public void disableConnection(@PathVariable("connectionId") Long connectionId) {
        connectionService.disableConnection(connectionId, workspaceContext.getWorkspaceId());
    }

    @PostMapping("/{connectionId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public void enableConnection(@PathVariable("connectionId") Long connectionId) {
        connectionService.enableConnection(connectionId, workspaceContext.getWorkspaceId());
    }

    @DeleteMapping("/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER')")
    public void archiveConnection(@PathVariable("connectionId") Long connectionId) {
        connectionService.archiveConnection(connectionId, workspaceContext.getWorkspaceId());
    }

    @GetMapping("/{connectionId}/sync-state")
    public List<SyncStateResponse> getSyncStates(@PathVariable("connectionId") Long connectionId) {
        return connectionService.getSyncStates(connectionId, workspaceContext.getWorkspaceId());
    }
}
