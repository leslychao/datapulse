package io.datapulse.execution.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkRejectRequest(
    @NotEmpty List<Long> actionIds,
    @NotBlank String cancelReason
) {
}
