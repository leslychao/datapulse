package io.datapulse.bidding.domain;

/**
 * Result produced by a bidding strategy evaluation for a single offer.
 *
 * @param decisionType        what the strategy recommends (BID_UP, BID_DOWN, HOLD, etc.)
 * @param targetBid           recommended bid in kopecks (null for HOLD)
 * @param explanation         human-readable explanation of why this decision was made
 * @param suggestedTransition if set, the strategy recommends transitioning assignment
 *                            to this strategy type (e.g. LAUNCH → ECONOMY_HOLD)
 */
public record BiddingStrategyResult(
    BidDecisionType decisionType,
    Integer targetBid,
    String explanation,
    BiddingStrategyType suggestedTransition
) {

  public BiddingStrategyResult(
      BidDecisionType decisionType,
      Integer targetBid,
      String explanation) {
    this(decisionType, targetBid, explanation, null);
  }

  public static BiddingStrategyResult hold() {
    return new BiddingStrategyResult(BidDecisionType.HOLD, null, "Insufficient data");
  }

  public static BiddingStrategyResult hold(String explanation) {
    return new BiddingStrategyResult(BidDecisionType.HOLD, null, explanation);
  }
}
