package io.datapulse.bidding.api;

import java.time.OffsetDateTime;

public record ManualBidLockResponse(
    long id,
    long marketplaceOfferId,
    Integer lockedBid,
    String reason,
    Long lockedBy,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt
) {
}
