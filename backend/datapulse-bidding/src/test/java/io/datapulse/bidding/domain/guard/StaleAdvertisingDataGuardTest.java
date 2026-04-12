package io.datapulse.bidding.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.datapulse.bidding.config.BiddingProperties;
import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;

class StaleAdvertisingDataGuardTest {

  private final BiddingProperties props = new BiddingProperties();
  private final StaleAdvertisingDataGuard guard = new StaleAdvertisingDataGuard(props);

  @Test
  @DisplayName("blocks when no recent activity (all zeros)")
  void should_block_when_noRecentData() {
    BiddingGuardContext ctx = context(TestSignals.withNoActivity());

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isFalse();
    assertThat(result.guardName()).isEqualTo("stale_advertising_data_guard");
  }

  @Test
  @DisplayName("allows when there is recent advertising activity")
  void should_allow_when_dataFresh() {
    BiddingGuardContext ctx = context(TestSignals.withActivity(100, 10, 5));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }

  @Test
  @DisplayName("blocks when hoursSinceLastChange exceeds threshold")
  void should_block_when_hoursSinceLastChangeTooHigh() {
    BiddingGuardContext ctx = context(TestSignals.withHoursSinceLastChange(72));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isFalse();
  }

  @Test
  @DisplayName("allows when hoursSinceLastChange is within threshold")
  void should_allow_when_hoursSinceLastChangeWithinThreshold() {
    BiddingGuardContext ctx = context(TestSignals.withHoursSinceLastChange(24));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }

  private BiddingGuardContext context(io.datapulse.bidding.domain.BiddingSignalSet signals) {
    return new BiddingGuardContext(
        100L, 1L, signals,
        BidDecisionType.BID_UP, 1000, 900, null);
  }
}
