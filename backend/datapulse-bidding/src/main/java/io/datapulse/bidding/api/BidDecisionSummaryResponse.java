package io.datapulse.bidding.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record BidDecisionSummaryResponse(
    long id,
    long marketplaceOfferId,
    String strategyType,
    String decisionType,
    Integer currentBid,
    Integer targetBid,
    String explanationSummary,
    String explanationKey,
    JsonNode explanationArgs,
    String executionMode,
    OffsetDateTime createdAt
) {
}
