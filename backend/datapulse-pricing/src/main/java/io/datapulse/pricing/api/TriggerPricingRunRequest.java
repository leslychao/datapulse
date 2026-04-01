package io.datapulse.pricing.api;

import jakarta.validation.constraints.NotNull;

public record TriggerPricingRunRequest(
        @NotNull Long connectionId
) {
}
