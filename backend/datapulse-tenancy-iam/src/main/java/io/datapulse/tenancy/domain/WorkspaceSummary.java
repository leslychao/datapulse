package io.datapulse.tenancy.domain;

public record WorkspaceSummary(
    long id,
    String name,
    String slug,
    WorkspaceStatus status,
    long tenantId,
    String tenantName,
    long connectionsCount,
    long membersCount
) {}
