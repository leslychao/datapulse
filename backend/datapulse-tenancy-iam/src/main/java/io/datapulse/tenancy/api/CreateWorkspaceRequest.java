package io.datapulse.tenancy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
        @NotBlank @Size(max = 255) String name
) {}
