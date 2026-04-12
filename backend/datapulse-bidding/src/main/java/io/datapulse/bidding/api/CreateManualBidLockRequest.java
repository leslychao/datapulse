package io.datapulse.bidding.api;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CreateManualBidLockRequest(
    @NotNull Long marketplaceOfferId,
    Integer lockedBid,
    String reason,
    OffsetDateTime expiresAt
) {
}
