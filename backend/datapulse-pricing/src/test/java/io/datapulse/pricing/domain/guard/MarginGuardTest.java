package io.datapulse.pricing.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

class MarginGuardTest {

  private final MarginGuard guard = new MarginGuard();

  private static final BigDecimal THRESHOLD_10_PCT = new BigDecimal("0.10");

  @Test
  @DisplayName("guard name is 'margin_guard'")
  void should_returnCorrectName() {
    assertThat(guard.guardName()).isEqualTo("margin_guard");
  }

  @Test
  @DisplayName("order is 40")
  void should_returnOrder40() {
    assertThat(guard.order()).isEqualTo(40);
  }

  @Nested
  @DisplayName("when threshold is null (not configured)")
  class NullThreshold {

    @Test
    @DisplayName("passes regardless of margin value")
    void should_pass_when_thresholdIsNull() {
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("1000"));
      BigDecimal targetPrice = new BigDecimal("500");

      GuardResult result = guard.check(signals, targetPrice, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
    }
  }

  @Nested
  @DisplayName("when margin guard enabled with explicit threshold")
  class Enabled {

    private final GuardConfig configWithThreshold =
        GuardConfig.DEFAULTS.withMinMarginPct(THRESHOLD_10_PCT);

    @Test
    @DisplayName("blocks when margin is below threshold")
    void should_block_when_marginBelowThreshold() {
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("1000"));
      BigDecimal targetPrice = new BigDecimal("800");

      GuardResult result = guard.check(signals, targetPrice, configWithThreshold);

      assertThat(result.passed()).isFalse();
      assertThat(result.reason()).isEqualTo("pricing.guard.margin_below_threshold");
      assertThat(result.args()).containsKey("marginPct");
      assertThat(result.args()).containsKey("targetPrice");
      assertThat(result.args()).containsKey("cogs");
    }

    @Test
    @DisplayName("passes when margin is above threshold")
    void should_pass_when_marginAboveThreshold() {
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("500"));
      BigDecimal targetPrice = new BigDecimal("1000");

      GuardResult result = guard.check(signals, targetPrice, configWithThreshold);

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("passes when margin equals threshold exactly")
    void should_pass_when_marginEqualsThreshold() {
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("900"));
      BigDecimal targetPrice = new BigDecimal("1000");

      GuardResult result = guard.check(signals, targetPrice, configWithThreshold);

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("passes when COGS is null")
    void should_pass_when_cogsNull() {
      PricingSignalSet signals = signalsWithCogs(null);
      BigDecimal targetPrice = new BigDecimal("1000");

      GuardResult result = guard.check(signals, targetPrice, configWithThreshold);

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("passes when target price is null")
    void should_pass_when_targetPriceNull() {
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("500"));

      GuardResult result = guard.check(signals, null, configWithThreshold);

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("passes when target price is zero")
    void should_pass_when_targetPriceZero() {
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("500"));

      GuardResult result = guard.check(signals, BigDecimal.ZERO, configWithThreshold);

      assertThat(result.passed()).isTrue();
    }
  }

  @Nested
  @DisplayName("when margin guard disabled")
  class Disabled {

    @Test
    @DisplayName("passes even with negative margin")
    void should_pass_when_guardDisabled() {
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("1000"));
      BigDecimal targetPrice = new BigDecimal("500");
      GuardConfig config = new GuardConfig(
          false, null, null, null, null, null, null, null, null, null, null);

      GuardResult result = guard.check(signals, targetPrice, config);

      assertThat(result.passed()).isTrue();
    }
  }

  @Nested
  @DisplayName("edge cases")
  class EdgeCases {

    private final GuardConfig configWithThreshold =
        GuardConfig.DEFAULTS.withMinMarginPct(THRESHOLD_10_PCT);

    @Test
    @DisplayName("blocks when target barely below threshold margin")
    void should_block_when_slightlyBelowThreshold() {
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("910.00"));
      BigDecimal targetPrice = new BigDecimal("1000.00");

      GuardResult result = guard.check(signals, targetPrice, configWithThreshold);

      assertThat(result.passed()).isFalse();
      assertThat(result.args()).containsKey("marginPct");
    }

    @Test
    @DisplayName("passes when COGS is zero")
    void should_pass_when_cogsIsZero() {
      PricingSignalSet signals = signalsWithCogs(BigDecimal.ZERO);
      BigDecimal targetPrice = new BigDecimal("100");

      GuardResult result = guard.check(signals, targetPrice, configWithThreshold);

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("reports correct negative margin percentage in args")
    void should_reportMarginPct_when_marginNegative() {
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("1000"));
      BigDecimal targetPrice = new BigDecimal("500");

      GuardResult result = guard.check(signals, targetPrice, configWithThreshold);

      assertThat(result.passed()).isFalse();
      Object marginPct = result.args().get("marginPct");
      assertThat(marginPct).isNotNull();
    }

    @Test
    @DisplayName("blocks when threshold is zero and margin is negative")
    void should_block_when_thresholdZeroAndMarginNegative() {
      GuardConfig zeroThreshold = GuardConfig.DEFAULTS.withMinMarginPct(BigDecimal.ZERO);
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("1000"));
      BigDecimal targetPrice = new BigDecimal("800");

      GuardResult result = guard.check(signals, targetPrice, zeroThreshold);

      assertThat(result.passed()).isFalse();
    }
  }

  private PricingSignalSet signalsWithCogs(BigDecimal cogs) {
    return new PricingSignalSet(
        new BigDecimal("1000"), cogs, null, null,
        false, false,
        null, null, null, null, null, null, null, null,
        null, null, null,
        null, null,
        null, null, null);
  }
}
