package io.datapulse.execution.api;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkApproveRequest(
        @NotEmpty List<Long> actionIds
) {
}
