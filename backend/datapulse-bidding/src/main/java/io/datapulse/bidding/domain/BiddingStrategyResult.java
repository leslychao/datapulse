package io.datapulse.bidding.domain;

/**
 * Result produced by a bidding strategy evaluation for a single offer.
 *
 * @param decisionType  what the strategy recommends (BID_UP, BID_DOWN, HOLD, etc.)
 * @param targetBid     recommended bid in kopecks (null for HOLD)
 * @param explanation   human-readable explanation of why this decision was made
 */
public record BiddingStrategyResult(
    BidDecisionType decisionType,
    Integer targetBid,
    String explanation
) {
}
