package io.datapulse.tenancy.api;

import io.datapulse.tenancy.domain.WorkspaceStatus;

import java.time.OffsetDateTime;

public record WorkspaceResponse(
        long id,
        String name,
        String slug,
        WorkspaceStatus status,
        OffsetDateTime createdAt,
        long tenantId,
        String tenantName,
        String tenantSlug
) {}
