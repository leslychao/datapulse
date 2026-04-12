package io.datapulse.bidding.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;

class CampaignInactiveGuardTest {

  private final CampaignInactiveGuard guard = new CampaignInactiveGuard();

  @Test
  @DisplayName("blocks when campaign status is not active ('9')")
  void should_block_when_statusNotActive() {
    BiddingGuardContext ctx = context(TestSignals.withCampaignStatus("7"));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isFalse();
    assertThat(result.guardName()).isEqualTo("campaign_inactive_guard");
  }

  @Test
  @DisplayName("blocks when campaign status is null")
  void should_block_when_statusNull() {
    BiddingGuardContext ctx = context(TestSignals.withCampaignStatus(null));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isFalse();
  }

  @Test
  @DisplayName("allows when campaign status is active ('9')")
  void should_allow_when_statusActive() {
    BiddingGuardContext ctx = context(TestSignals.withCampaignStatus("9"));

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }

  private BiddingGuardContext context(io.datapulse.bidding.domain.BiddingSignalSet signals) {
    return new BiddingGuardContext(
        100L, 1L, signals,
        BidDecisionType.BID_UP, 1000, 900, null);
  }
}
