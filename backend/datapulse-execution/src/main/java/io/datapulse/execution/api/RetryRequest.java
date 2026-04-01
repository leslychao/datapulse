package io.datapulse.execution.api;

import jakarta.validation.constraints.NotBlank;

public record RetryRequest(
        @NotBlank String retryReason
) {
}
