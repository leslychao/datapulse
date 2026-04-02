package io.datapulse.tenancy.domain;

import java.util.List;

public record UserProfile(
    long id,
    String email,
    String name,
    boolean needsOnboarding,
    List<WorkspaceMembership> memberships
) {

  public record WorkspaceMembership(
      long workspaceId,
      String workspaceName,
      long tenantId,
      String tenantName,
      String role
  ) {}
}
