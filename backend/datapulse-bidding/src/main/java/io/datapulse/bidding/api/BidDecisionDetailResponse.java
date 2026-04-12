package io.datapulse.bidding.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record BidDecisionDetailResponse(
    long id,
    long biddingRunId,
    long marketplaceOfferId,
    String strategyType,
    String decisionType,
    Integer currentBid,
    Integer targetBid,
    JsonNode signalSnapshot,
    JsonNode guardsApplied,
    String explanationSummary,
    String executionMode,
    OffsetDateTime createdAt
) {
}
