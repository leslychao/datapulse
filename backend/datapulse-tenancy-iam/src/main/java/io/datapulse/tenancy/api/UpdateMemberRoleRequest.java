package io.datapulse.tenancy.api;

import io.datapulse.tenancy.domain.MemberRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull MemberRole role
) {}
