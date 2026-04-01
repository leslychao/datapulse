package io.datapulse.execution.api;

import jakarta.validation.constraints.NotNull;

public record ResetShadowStateRequest(
        @NotNull Long connectionId
) {
}
