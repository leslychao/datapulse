package io.datapulse.integration.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateConnectionRequest(
        @NotBlank String name
) {
}
