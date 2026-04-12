package io.datapulse.bidding.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;

class StockOutGuardTest {

  private final StockOutGuard guard = new StockOutGuard();

  @Test
  @DisplayName("blocks BID_UP when stock is depleted (stockDays == 0)")
  void should_block_bidUp_when_outOfStock() {
    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withStockDays(0),
        BidDecisionType.BID_UP, 1000, 900, null);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isFalse();
    assertThat(result.guardName()).isEqualTo("stock_out_guard");
  }

  @Test
  @DisplayName("allows BID_DOWN even when stock is depleted")
  void should_allow_bidDown_when_outOfStock() {
    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withStockDays(0),
        BidDecisionType.BID_DOWN, 800, 900, null);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }

  @Test
  @DisplayName("allows BID_UP when stock is available")
  void should_allow_when_stockAvailable() {
    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withStockDays(15),
        BidDecisionType.BID_UP, 1000, 900, null);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }
}
