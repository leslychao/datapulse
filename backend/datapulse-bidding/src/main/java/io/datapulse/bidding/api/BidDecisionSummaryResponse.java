package io.datapulse.bidding.api;

import java.time.OffsetDateTime;

public record BidDecisionSummaryResponse(
    long id,
    long marketplaceOfferId,
    String strategyType,
    String decisionType,
    Integer currentBid,
    Integer targetBid,
    String explanationSummary,
    String executionMode,
    OffsetDateTime createdAt
) {
}
