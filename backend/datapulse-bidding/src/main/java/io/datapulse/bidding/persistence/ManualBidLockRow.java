package io.datapulse.bidding.persistence;

import java.time.Instant;

public record ManualBidLockRow(
    long id,
    Integer lockedBid,
    String reason,
    Instant expiresAt
) {
}
