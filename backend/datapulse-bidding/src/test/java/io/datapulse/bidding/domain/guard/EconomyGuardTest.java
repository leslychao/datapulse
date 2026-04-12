package io.datapulse.bidding.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;

class EconomyGuardTest {

  private final EconomyGuard guard = new EconomyGuard();

  @Test
  @DisplayName("blocks BID_UP when margin is negative")
  void should_block_bidUp_when_negativeMargin() {
    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withMarginPct(new BigDecimal("-5.0")),
        BidDecisionType.BID_UP, 1000, 900, null);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isFalse();
    assertThat(result.guardName()).isEqualTo("economy_guard");
  }

  @Test
  @DisplayName("blocks BID_UP when margin is zero")
  void should_block_bidUp_when_zeroMargin() {
    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withMarginPct(BigDecimal.ZERO),
        BidDecisionType.BID_UP, 1000, 900, null);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isFalse();
  }

  @Test
  @DisplayName("allows BID_UP when margin is positive")
  void should_allow_when_positiveMargin() {
    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withMarginPct(new BigDecimal("15.0")),
        BidDecisionType.BID_UP, 1000, 900, null);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }

  @Test
  @DisplayName("allows BID_DOWN even with negative margin")
  void should_allow_bidDown_when_negativeMargin() {
    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withMarginPct(new BigDecimal("-5.0")),
        BidDecisionType.BID_DOWN, 800, 900, null);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }
}
