package io.datapulse.tenancy.api;

import io.datapulse.tenancy.domain.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInvitationRequest(
        @NotBlank @Email String email,
        @NotNull MemberRole role
) {}
