package io.datapulse.execution.api;

import jakarta.validation.constraints.NotBlank;

public record HoldRequest(
        @NotBlank String holdReason
) {
}
