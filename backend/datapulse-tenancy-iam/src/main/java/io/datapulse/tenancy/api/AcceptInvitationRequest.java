package io.datapulse.tenancy.api;

import jakarta.validation.constraints.NotBlank;

public record AcceptInvitationRequest(
        @NotBlank String token
) {}
