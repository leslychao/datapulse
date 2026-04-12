package io.datapulse.bidding.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.datapulse.bidding.config.BiddingProperties;
import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;

class FrequencyGuardTest {

  private final BiddingProperties props = new BiddingProperties();
  private final FrequencyGuard guard = new FrequencyGuard(props);

  @Test
  @DisplayName("blocks when bid was changed recently (within min interval hours)")
  void should_block_when_recentDecision() {
    BiddingGuardContext ctx = context(TestSignals.withHoursSinceLastChange(2));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isFalse();
    assertThat(result.guardName()).isEqualTo("frequency_guard");
  }

  @Test
  @DisplayName("allows when enough hours have passed since last change")
  void should_allow_when_enoughTimePassed() {
    BiddingGuardContext ctx = context(TestSignals.withHoursSinceLastChange(10));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }

  @Test
  @DisplayName("allows when hoursSinceLastChange is null (no previous change)")
  void should_allow_when_noPreviousChange() {
    BiddingGuardContext ctx = context(TestSignals.withHoursSinceLastChange(null));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }

  @Test
  @DisplayName("blocks at exact boundary (equal to minIntervalHours - 1)")
  void should_block_atBoundary() {
    BiddingGuardContext ctx = context(TestSignals.withHoursSinceLastChange(3));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isFalse();
  }

  @Test
  @DisplayName("allows at exact threshold (equal to minIntervalHours)")
  void should_allow_atExactThreshold() {
    BiddingGuardContext ctx = context(TestSignals.withHoursSinceLastChange(4));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }

  private BiddingGuardContext context(io.datapulse.bidding.domain.BiddingSignalSet signals) {
    return new BiddingGuardContext(
        100L, 1L, signals,
        BidDecisionType.BID_UP, 1000, 900, null);
  }
}
