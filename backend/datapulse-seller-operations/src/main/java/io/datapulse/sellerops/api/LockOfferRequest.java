package io.datapulse.sellerops.api;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LockOfferRequest(
    @NotNull BigDecimal lockedPrice,
    String reason,
    Integer durationHours
) {
}
