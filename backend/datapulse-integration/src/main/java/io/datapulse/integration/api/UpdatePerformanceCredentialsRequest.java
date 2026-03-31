package io.datapulse.integration.api;

import jakarta.validation.constraints.NotBlank;

public record UpdatePerformanceCredentialsRequest(
        @NotBlank String performanceClientId,
        @NotBlank String performanceClientSecret
) {
}
