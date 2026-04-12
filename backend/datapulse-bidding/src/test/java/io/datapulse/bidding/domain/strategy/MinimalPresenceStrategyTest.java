package io.datapulse.bidding.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BidUnit;
import io.datapulse.bidding.domain.BiddingSignalSet;
import io.datapulse.bidding.domain.BiddingStrategyResult;
import io.datapulse.bidding.domain.BiddingStrategyType;

class MinimalPresenceStrategyTest {

  private final MinimalPresenceStrategy strategy = new MinimalPresenceStrategy();

  @Test
  @DisplayName("strategyType is MINIMAL_PRESENCE")
  void should_returnMinimalPresenceType() {
    assertThat(strategy.strategyType()).isEqualTo(BiddingStrategyType.MINIMAL_PRESENCE);
  }

  @Nested
  @DisplayName("BidDown scenarios")
  class BidDown {

    @Test
    @DisplayName("bids down when currentBid is above minBid")
    void should_bidDown_when_currentBidAboveMinBid() {
      BiddingSignalSet signals = signals(200, 50, 10);

      BiddingStrategyResult result = strategy.evaluate(signals, JsonNodeFactory.instance.objectNode());

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.BID_DOWN);
      assertThat(result.targetBid()).isEqualTo(50);
    }
  }

  @Nested
  @DisplayName("BidUp scenarios")
  class BidUp {

    @Test
    @DisplayName("bids up when currentBid is below minBid")
    void should_bidUp_when_currentBidBelowMinBid() {
      BiddingSignalSet signals = signals(30, 50, 10);

      BiddingStrategyResult result = strategy.evaluate(signals, JsonNodeFactory.instance.objectNode());

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.BID_UP);
      assertThat(result.targetBid()).isEqualTo(50);
    }
  }

  @Nested
  @DisplayName("Hold scenarios")
  class Hold {

    @Test
    @DisplayName("holds when currentBid equals minBid")
    void should_hold_when_currentBidEqualsMinBid() {
      BiddingSignalSet signals = signals(50, 50, 10);

      BiddingStrategyResult result = strategy.evaluate(signals, JsonNodeFactory.instance.objectNode());

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.HOLD);
      assertThat(result.targetBid()).isEqualTo(50);
    }

    @Test
    @DisplayName("holds when minBid is null")
    void should_hold_when_minBidIsNull() {
      BiddingSignalSet signals = signals(100, null, 10);

      BiddingStrategyResult result = strategy.evaluate(signals, JsonNodeFactory.instance.objectNode());

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.HOLD);
    }

    @Test
    @DisplayName("holds when currentBid is null")
    void should_hold_when_currentBidIsNull() {
      BiddingSignalSet signals = signals(null, 50, 10);

      BiddingStrategyResult result = strategy.evaluate(signals, JsonNodeFactory.instance.objectNode());

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.HOLD);
    }
  }

  @Nested
  @DisplayName("Pause scenarios")
  class Pause {

    @Test
    @DisplayName("pauses when stockDays is zero")
    void should_pause_when_outOfStock() {
      BiddingSignalSet signals = signals(100, 50, 0);

      BiddingStrategyResult result = strategy.evaluate(signals, JsonNodeFactory.instance.objectNode());

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.PAUSE);
    }
  }

  private BiddingSignalSet signals(Integer currentBid, Integer minBid, Integer stockDays) {
    return new BiddingSignalSet(
        currentBid, null, null, null,
        0, 0, 0, BigDecimal.ZERO,
        null, stockDays, null, null, minBid,
        null, null, "9", BidUnit.KOPECKS);
  }
}
