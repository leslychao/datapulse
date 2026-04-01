package io.datapulse.audit.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateAlertRuleRequest(
        @NotNull String config,
        @NotNull Boolean enabled,
        @NotBlank String severity,
        @NotNull Boolean blocksAutomation
) {
}
