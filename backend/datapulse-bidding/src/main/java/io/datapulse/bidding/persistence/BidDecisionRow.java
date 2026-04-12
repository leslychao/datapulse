package io.datapulse.bidding.persistence;

import java.time.Instant;

public record BidDecisionRow(
    long id,
    String decisionType,
    Integer targetBid,
    Instant createdAt
) {
}
