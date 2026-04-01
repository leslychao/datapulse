package io.datapulse.promotions.api;

import jakarta.validation.constraints.NotNull;

public record TriggerPromoEvaluationRunRequest(
        @NotNull Long connectionId
) {
}
