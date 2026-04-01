package io.datapulse.promotions.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkRejectPromoActionRequest(
        @NotEmpty List<Long> actionIds,
        @NotBlank String reason
) {
}
