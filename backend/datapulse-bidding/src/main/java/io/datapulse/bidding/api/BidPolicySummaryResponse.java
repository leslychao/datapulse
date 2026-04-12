package io.datapulse.bidding.api;

import java.time.OffsetDateTime;

public record BidPolicySummaryResponse(
    long id,
    String name,
    String strategyType,
    String executionMode,
    String status,
    int assignmentCount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
