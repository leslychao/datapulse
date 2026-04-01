package io.datapulse.pricing.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateManualLockRequest(
        @NotNull Long marketplaceOfferId,
        @NotNull @DecimalMin("0.01") BigDecimal lockedPrice,
        String reason,
        OffsetDateTime expiresAt
) {
}
