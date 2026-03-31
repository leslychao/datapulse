package io.datapulse.tenancy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank @Size(max = 255) String name
) {}
