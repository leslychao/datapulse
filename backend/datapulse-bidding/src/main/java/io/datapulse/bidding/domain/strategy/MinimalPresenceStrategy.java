package io.datapulse.bidding.domain.strategy;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingSignalSet;
import io.datapulse.bidding.domain.BiddingStrategyResult;
import io.datapulse.bidding.domain.BiddingStrategyType;
import io.datapulse.bidding.domain.ExplanationMessage;
import io.datapulse.bidding.domain.PauseReasonCode;

import java.util.Map;

/**
 * MINIMAL_PRESENCE — keeps the bid at the marketplace minimum allowed level.
 * Goal: maintain ad presence at the lowest possible cost.
 * Pauses bidding entirely when stock is depleted.
 */
@Component
public class MinimalPresenceStrategy implements BiddingStrategy {

  @Override
  public BiddingStrategyType strategyType() {
    return BiddingStrategyType.MINIMAL_PRESENCE;
  }

  @Override
  public BiddingStrategyResult evaluate(BiddingSignalSet signals, JsonNode policyConfig) {
    if (signals.stockDays() != null && signals.stockDays() == 0) {
      return BiddingStrategyResult.pause(PauseReasonCode.STOCK_OUT,
          "Minimal presence: stockDays=0. Decision: PAUSE",
          ExplanationMessage.of("bidding.strategy.minimal_presence.pause_stock_out"));
    }

    Integer minBid = signals.minBid();
    if (minBid == null) {
      return BiddingStrategyResult.hold(
          "Minimal presence: minBid is null — no data. Decision: HOLD",
          ExplanationMessage.of("bidding.strategy.minimal_presence.no_min_bid"));
    }

    Integer currentBid = signals.currentBid();
    if (currentBid == null) {
      return BiddingStrategyResult.hold(
          "Minimal presence: currentBid is null — no data. Decision: HOLD",
          ExplanationMessage.of("bidding.strategy.minimal_presence.no_current_bid"));
    }

    Map<String, Object> args = Map.of(
        "currentBid", currentBid, "minBid", minBid);

    if (currentBid > minBid) {
      return BiddingStrategyResult.withMessage(
          BidDecisionType.BID_DOWN, minBid,
          "Minimal presence: current=%d, min=%d. Decision: BID_DOWN"
              .formatted(currentBid, minBid),
          "bidding.strategy.minimal_presence.bid_down", args);
    }

    if (currentBid < minBid) {
      return BiddingStrategyResult.withMessage(
          BidDecisionType.BID_UP, minBid,
          "Minimal presence: current=%d, min=%d. Decision: BID_UP"
              .formatted(currentBid, minBid),
          "bidding.strategy.minimal_presence.bid_up", args);
    }

    return BiddingStrategyResult.withMessage(
        BidDecisionType.HOLD, currentBid,
        "Minimal presence: current=%d, min=%d. Decision: HOLD"
            .formatted(currentBid, minBid),
        "bidding.strategy.minimal_presence.hold", args);
  }
}
