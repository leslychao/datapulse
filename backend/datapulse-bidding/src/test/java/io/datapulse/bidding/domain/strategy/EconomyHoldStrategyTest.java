package io.datapulse.bidding.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingSignalSet;
import io.datapulse.bidding.domain.BiddingStrategyResult;

class EconomyHoldStrategyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final EconomyHoldStrategy strategy = new EconomyHoldStrategy();

  @Test
  @DisplayName("strategyType is ECONOMY_HOLD")
  void should_returnEconomyHoldType() {
    assertThat(strategy.strategyType())
        .isEqualTo(io.datapulse.bidding.domain.BiddingStrategyType.ECONOMY_HOLD);
  }

  @Nested
  @DisplayName("BidUp scenarios")
  class BidUp {

    @Test
    @DisplayName("bids up when DRR is below lower bound and ROAS above min")
    void should_bidUp_when_drrBelowLowerBound_and_roasAboveMin() {
      BiddingSignalSet signals = signals(1000, new BigDecimal("3.0"),
          new BigDecimal("5.0"), null, 50);
      JsonNode config = config("10.0", "10", "10", "15", null);

      BiddingStrategyResult result = strategy.evaluate(signals, config);

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.BID_UP);
      assertThat(result.targetBid()).isGreaterThan(1000);
    }

    @Test
    @DisplayName("respects maxBid when bidUp exceeds max")
    void should_respectMaxBid_when_bidUpExceedsMax() {
      BiddingSignalSet signals = signals(900, new BigDecimal("3.0"),
          new BigDecimal("5.0"), null, 50);
      JsonNode config = config("10.0", "10", "50", "15", "950");

      BiddingStrategyResult result = strategy.evaluate(signals, config);

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.BID_UP);
      assertThat(result.targetBid()).isLessThanOrEqualTo(950);
    }
  }

  @Nested
  @DisplayName("BidDown scenarios")
  class BidDown {

    @Test
    @DisplayName("bids down when DRR exceeds upper bound")
    void should_bidDown_when_drrAboveUpperBound() {
      BiddingSignalSet signals = signals(1000, new BigDecimal("25.0"),
          new BigDecimal("2.0"), null, 50);
      JsonNode config = config("10.0", "10", "10", "15", null);

      BiddingStrategyResult result = strategy.evaluate(signals, config);

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.BID_DOWN);
      assertThat(result.targetBid()).isLessThan(1000);
    }

    @Test
    @DisplayName("clamps bid down to min bid floor")
    void should_useMinBid_when_bidDownBelowMin() {
      BiddingSignalSet signals = signals(60, new BigDecimal("25.0"),
          new BigDecimal("2.0"), null, 50);
      JsonNode config = config("10.0", "10", "10", "90", null);

      BiddingStrategyResult result = strategy.evaluate(signals, config);

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.BID_DOWN);
      assertThat(result.targetBid()).isGreaterThanOrEqualTo(50);
    }
  }

  @Nested
  @DisplayName("Hold scenarios")
  class Hold {

    @Test
    @DisplayName("holds when DRR is within tolerance band")
    void should_hold_when_drrWithinTolerance() {
      BiddingSignalSet signals = signals(1000, new BigDecimal("10.0"),
          new BigDecimal("5.0"), null, 50);
      JsonNode config = config("10.0", "10", "10", "15", null);

      BiddingStrategyResult result = strategy.evaluate(signals, config);

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.HOLD);
      assertThat(result.targetBid()).isEqualTo(1000);
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    @DisplayName("holds when currentBid is null")
    void should_hold_when_currentBidIsNull() {
      BiddingSignalSet signals = signals(null, new BigDecimal("10.0"),
          new BigDecimal("5.0"), null, 50);
      JsonNode config = config("10.0", "10", "10", "15", null);

      BiddingStrategyResult result = strategy.evaluate(signals, config);

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.HOLD);
    }

    @Test
    @DisplayName("holds when drrPct is null")
    void should_hold_when_drrIsNull() {
      BiddingSignalSet signals = signals(1000, null,
          new BigDecimal("5.0"), null, 50);
      JsonNode config = config("10.0", "10", "10", "15", null);

      BiddingStrategyResult result = strategy.evaluate(signals, config);

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.HOLD);
    }

    @Test
    @DisplayName("holds when targetDrrPct is not configured")
    void should_hold_when_targetDrrNotConfigured() {
      BiddingSignalSet signals = signals(1000, new BigDecimal("10.0"),
          new BigDecimal("5.0"), null, 50);
      ObjectNode config = MAPPER.createObjectNode();

      BiddingStrategyResult result = strategy.evaluate(signals, config);

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.HOLD);
    }

    @Test
    @DisplayName("does not bid up when ROAS is below minRoas")
    void should_hold_when_roasBelowMin() {
      BiddingSignalSet signals = signals(1000, new BigDecimal("3.0"),
          new BigDecimal("0.5"), null, 50);
      JsonNode config = config("10.0", "10", "10", "15", null);

      BiddingStrategyResult result = strategy.evaluate(signals, config);

      assertThat(result.decisionType()).isEqualTo(BidDecisionType.HOLD);
    }
  }

  private BiddingSignalSet signals(
      Integer currentBid, BigDecimal drrPct, BigDecimal roas,
      BigDecimal marginPct, Integer minBid) {

    return new BiddingSignalSet(
        currentBid, drrPct, null, roas,
        100, 10, 5, BigDecimal.TEN,
        marginPct, 30, null, null, minBid,
        null, null, "9");
  }

  private JsonNode config(String targetDrrPct, String tolerancePct,
      String stepUpPct, String stepDownPct, String maxBidKopecks) {

    ObjectNode node = MAPPER.createObjectNode();
    if (targetDrrPct != null) node.put("targetDrrPct", new BigDecimal(targetDrrPct));
    if (tolerancePct != null) node.put("tolerancePct", new BigDecimal(tolerancePct));
    if (stepUpPct != null) node.put("stepUpPct", new BigDecimal(stepUpPct));
    if (stepDownPct != null) node.put("stepDownPct", new BigDecimal(stepDownPct));
    if (maxBidKopecks != null) node.put("maxBidKopecks", Integer.parseInt(maxBidKopecks));
    return node;
  }
}
