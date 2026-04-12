package io.datapulse.bidding.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.datapulse.bidding.config.BiddingProperties;
import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;

class FrequencyGuardTest {

  private final BiddingProperties props = new BiddingProperties(7, 50, 48, 48, 7, true);
  private final FrequencyGuard guard = new FrequencyGuard(props);

  @Test
  @DisplayName("blocks when bid was changed recently (within min interval)")
  void should_block_when_recentDecision() {
    BiddingGuardContext ctx = context(TestSignals.withDaysSinceLastChange(1));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isFalse();
    assertThat(result.guardName()).isEqualTo("frequency_guard");
  }

  @Test
  @DisplayName("allows when enough time has passed since last change")
  void should_allow_when_enoughTimePassed() {
    BiddingGuardContext ctx = context(TestSignals.withDaysSinceLastChange(5));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }

  @Test
  @DisplayName("allows when daysSinceLastChange is null (no previous change)")
  void should_allow_when_noPreviousChange() {
    BiddingGuardContext ctx = context(TestSignals.withDaysSinceLastChange(null));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }

  private BiddingGuardContext context(io.datapulse.bidding.domain.BiddingSignalSet signals) {
    return new BiddingGuardContext(
        100L, 1L, signals,
        BidDecisionType.BID_UP, 1000, 900, null);
  }
}
