package io.datapulse.bidding.domain;

import java.util.Map;

/**
 * Result produced by a bidding strategy evaluation for a single offer.
 *
 * @param decisionType         what the strategy recommends (BID_UP, BID_DOWN, HOLD, etc.)
 * @param targetBid            recommended bid in kopecks (null for HOLD)
 * @param explanation          human-readable explanation (kept for CSV export and logs)
 * @param explanationMessage   structured i18n key + args for frontend translation
 * @param suggestedTransition  if set, recommends transitioning to this strategy type
 * @param pauseReasonCode      structured reason for PAUSE decisions (null for non-PAUSE)
 */
public record BiddingStrategyResult(
    BidDecisionType decisionType,
    Integer targetBid,
    String explanation,
    ExplanationMessage explanationMessage,
    BiddingStrategyType suggestedTransition,
    PauseReasonCode pauseReasonCode
) {

  public BiddingStrategyResult(
      BidDecisionType decisionType,
      Integer targetBid,
      String explanation) {
    this(decisionType, targetBid, explanation, null, null, null);
  }

  public BiddingStrategyResult(
      BidDecisionType decisionType,
      Integer targetBid,
      String explanation,
      ExplanationMessage explanationMessage) {
    this(decisionType, targetBid, explanation, explanationMessage, null, null);
  }

  public BiddingStrategyResult(
      BidDecisionType decisionType,
      Integer targetBid,
      String explanation,
      BiddingStrategyType suggestedTransition) {
    this(decisionType, targetBid, explanation, null, suggestedTransition, null);
  }

  public static BiddingStrategyResult hold() {
    return new BiddingStrategyResult(
        BidDecisionType.HOLD, null, "Insufficient data",
        ExplanationMessage.of("bidding.strategy.insufficient_data"));
  }

  public static BiddingStrategyResult hold(String explanation) {
    return new BiddingStrategyResult(BidDecisionType.HOLD, null, explanation);
  }

  public static BiddingStrategyResult hold(
      String explanation, ExplanationMessage message) {
    return new BiddingStrategyResult(
        BidDecisionType.HOLD, null, explanation, message);
  }

  public static BiddingStrategyResult pause(
      PauseReasonCode reason, String explanation) {
    return new BiddingStrategyResult(
        BidDecisionType.PAUSE, null, explanation, null, null, reason);
  }

  public static BiddingStrategyResult pause(
      PauseReasonCode reason, String explanation,
      ExplanationMessage message) {
    return new BiddingStrategyResult(
        BidDecisionType.PAUSE, null, explanation, message, null, reason);
  }

  public static BiddingStrategyResult withMessage(
      BidDecisionType type, Integer targetBid,
      String explanation, String messageKey,
      Map<String, Object> args) {
    return new BiddingStrategyResult(
        type, targetBid, explanation,
        ExplanationMessage.of(messageKey, args), null, null);
  }
}
