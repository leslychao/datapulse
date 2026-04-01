package io.datapulse.execution.api;

import jakarta.validation.constraints.NotBlank;

public record CancelRequest(
        @NotBlank String cancelReason
) {
}
