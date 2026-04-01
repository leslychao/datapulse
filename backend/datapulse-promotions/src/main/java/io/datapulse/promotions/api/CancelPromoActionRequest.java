package io.datapulse.promotions.api;

import jakarta.validation.constraints.NotBlank;

public record CancelPromoActionRequest(
        @NotBlank String cancelReason
) {
}
