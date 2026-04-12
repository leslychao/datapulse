package io.datapulse.bidding.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.datapulse.bidding.config.BiddingProperties;
import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;

class LowStockGuardTest {

  private final BiddingProperties props =
      new BiddingProperties(7, 50, 4, 48, 7, true, 3, 7, 30);
  private final LowStockGuard guard = new LowStockGuard(props);

  @Test
  @DisplayName("blocks BID_UP when stock is low (below threshold)")
  void should_block_bidUp_when_lowStock() {
    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withStockDays(3),
        BidDecisionType.BID_UP, 1000, 900, null);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isFalse();
    assertThat(result.guardName()).isEqualTo("low_stock_guard");
    assertThat(result.args()).containsEntry("days", 3);
    assertThat(result.args()).containsEntry("threshold", 7);
  }

  @Test
  @DisplayName("allows BID_UP when stock is above threshold")
  void should_allow_when_enoughStock() {
    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withStockDays(15),
        BidDecisionType.BID_UP, 1000, 900, null);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }

  @Test
  @DisplayName("allows BID_DOWN even with low stock")
  void should_allow_bidDown_when_lowStock() {
    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withStockDays(3),
        BidDecisionType.BID_DOWN, 800, 900, null);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }
}
