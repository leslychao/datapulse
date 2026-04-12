package io.datapulse.bidding.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.datapulse.bidding.domain.BidDecisionType;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;

class DrrCeilingGuardTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final DrrCeilingGuard guard = new DrrCeilingGuard();

  @Test
  @DisplayName("blocks BID_UP when DRR exceeds ceiling")
  void should_block_bidUp_when_drrAboveCeiling() {
    ObjectNode config = MAPPER.createObjectNode();
    config.put("drrCeiling", new BigDecimal("25.0"));

    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withDrrPct(new BigDecimal("30.0")),
        BidDecisionType.BID_UP, 1000, 900, config);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isFalse();
    assertThat(result.guardName()).isEqualTo("drr_ceiling_guard");
    assertThat(result.args()).containsKey("drr");
    assertThat(result.args()).containsKey("ceiling");
  }

  @Test
  @DisplayName("allows BID_UP when DRR is below ceiling")
  void should_allow_when_drrBelowCeiling() {
    ObjectNode config = MAPPER.createObjectNode();
    config.put("drrCeiling", new BigDecimal("25.0"));

    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withDrrPct(new BigDecimal("15.0")),
        BidDecisionType.BID_UP, 1000, 900, config);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }

  @Test
  @DisplayName("allows BID_DOWN even when DRR exceeds ceiling")
  void should_allow_bidDown_when_drrAboveCeiling() {
    ObjectNode config = MAPPER.createObjectNode();
    config.put("drrCeiling", new BigDecimal("25.0"));

    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withDrrPct(new BigDecimal("30.0")),
        BidDecisionType.BID_DOWN, 800, 900, config);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }

  @Test
  @DisplayName("uses default ceiling (30%) when config is null")
  void should_useDefaultCeiling_when_configNull() {
    BiddingGuardContext ctx = new BiddingGuardContext(
        100L, 1L, TestSignals.withDrrPct(new BigDecimal("25.0")),
        BidDecisionType.BID_UP, 1000, 900, null);

    BiddingGuardResult result = guard.evaluate(ctx);

    assertThat(result.allowed()).isTrue();
  }
}
