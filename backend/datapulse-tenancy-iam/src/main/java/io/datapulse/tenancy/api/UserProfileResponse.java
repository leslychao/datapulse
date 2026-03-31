package io.datapulse.tenancy.api;

import java.util.List;

public record UserProfileResponse(
        long id,
        String email,
        String name,
        boolean needsOnboarding,
        List<MembershipResponse> memberships
) {

    public record MembershipResponse(
            long workspaceId,
            String workspaceName,
            long tenantId,
            String tenantName,
            String role
    ) {}
}
