package io.datapulse.tenancy.api;

import io.datapulse.tenancy.domain.MemberRole;

public record AcceptInvitationResponse(
        long workspaceId,
        String workspaceName,
        MemberRole role
) {}
