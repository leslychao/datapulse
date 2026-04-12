package io.datapulse.bidding.api;

import java.time.Instant;

public record BidActionSummaryResponse(
    long id,
    long marketplaceOfferId,
    String marketplaceType,
    String decisionType,
    Integer previousBid,
    Integer targetBid,
    String status,
    String executionMode,
    Instant createdAt
) {
}
