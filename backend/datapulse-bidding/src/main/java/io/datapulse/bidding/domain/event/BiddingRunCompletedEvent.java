package io.datapulse.bidding.domain.event;

/**
 * Published after a bidding run completes (COMPLETED or PAUSED).
 */
public record BiddingRunCompletedEvent(
    long workspaceId,
    long runId,
    long bidPolicyId,
    String status,
    int totalBidUp,
    int totalBidDown,
    int totalHold,
    int totalPause
) {
}
