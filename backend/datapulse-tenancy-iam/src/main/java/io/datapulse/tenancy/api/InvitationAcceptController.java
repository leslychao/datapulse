package io.datapulse.tenancy.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.tenancy.domain.InvitationService;
import io.datapulse.tenancy.persistence.WorkspaceInvitationEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/invitations", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class InvitationAcceptController {

    private final InvitationService invitationService;
    private final WorkspaceContext workspaceContext;

    @PostMapping("/accept")
    public AcceptInvitationResponse acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request) {
        WorkspaceInvitationEntity invitation = invitationService.acceptInvitation(
                request.token(), workspaceContext.getUserId());
        return new AcceptInvitationResponse(
                invitation.getWorkspace().getId(),
                invitation.getWorkspace().getName(),
                invitation.getRole());
    }
}
