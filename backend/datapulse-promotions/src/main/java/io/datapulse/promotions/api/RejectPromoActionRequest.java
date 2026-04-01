package io.datapulse.promotions.api;

import jakarta.validation.constraints.NotBlank;

public record RejectPromoActionRequest(
        @NotBlank String reason
) {
}
