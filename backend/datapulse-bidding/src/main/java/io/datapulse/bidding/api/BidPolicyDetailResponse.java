package io.datapulse.bidding.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record BidPolicyDetailResponse(
    long id,
    String name,
    String strategyType,
    String executionMode,
    String status,
    JsonNode config,
    int assignmentCount,
    Long createdBy,
    Long version,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
