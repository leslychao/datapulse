package io.datapulse.promotions.api;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkPromoActionRequest(
        @NotEmpty List<Long> actionIds
) {
}
