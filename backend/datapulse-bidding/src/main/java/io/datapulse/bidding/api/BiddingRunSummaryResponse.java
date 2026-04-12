package io.datapulse.bidding.api;

import java.time.OffsetDateTime;

public record BiddingRunSummaryResponse(
    long id,
    long bidPolicyId,
    String status,
    int totalEligible,
    int totalDecisions,
    int totalBidUp,
    int totalBidDown,
    int totalHold,
    int totalPause,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt
) {
}
