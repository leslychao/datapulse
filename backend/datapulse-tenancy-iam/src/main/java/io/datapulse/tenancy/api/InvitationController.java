package io.datapulse.tenancy.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.tenancy.domain.InvitationService;
import io.datapulse.tenancy.persistence.WorkspaceInvitationEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/invitations", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public List<InvitationResponse> listInvitations(@PathVariable("workspaceId") Long workspaceId) {
        return invitationService.listInvitations(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public InvitationResponse createInvitation(@PathVariable("workspaceId") Long workspaceId,
                                               @Valid @RequestBody CreateInvitationRequest request) {
        var entity = invitationService.createInvitation(
                workspaceId, workspaceContext.getUserId(),
                workspaceContext.getRole(), request.email(), request.role());
        return toResponse(entity);
    }

    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public void cancelInvitation(@PathVariable("workspaceId") Long workspaceId,
                                 @PathVariable("invitationId") Long invitationId) {
        invitationService.cancelInvitation(workspaceId, invitationId);
    }

    @PostMapping("/{invitationId}/resend")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public InvitationResponse resendInvitation(@PathVariable("workspaceId") Long workspaceId,
                                               @PathVariable("invitationId") Long invitationId) {
        var entity = invitationService.resendInvitation(workspaceId, invitationId);
        return toResponse(entity);
    }

    private InvitationResponse toResponse(WorkspaceInvitationEntity entity) {
        return new InvitationResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getRole(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getExpiresAt());
    }
}
