package io.datapulse.execution.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReconcileRequest(
        @NotNull ReconcileOutcome outcome,
        @NotBlank String manualOverrideReason
) {

    public enum ReconcileOutcome {
        SUCCEEDED,
        FAILED
    }
}
