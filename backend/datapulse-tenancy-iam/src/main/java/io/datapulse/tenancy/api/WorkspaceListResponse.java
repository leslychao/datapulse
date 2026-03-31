package io.datapulse.tenancy.api;

import io.datapulse.tenancy.domain.WorkspaceStatus;

public record WorkspaceListResponse(
        long id,
        String name,
        String slug,
        WorkspaceStatus status,
        long tenantId,
        String tenantName,
        long connectionsCount,
        long membersCount
) {}
