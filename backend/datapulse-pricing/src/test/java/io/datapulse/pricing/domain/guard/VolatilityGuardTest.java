package io.datapulse.pricing.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

class VolatilityGuardTest {

  private final VolatilityGuard guard = new VolatilityGuard();

  @Test
  @DisplayName("guard name is 'volatility_guard'")
  void should_returnCorrectName() {
    assertThat(guard.guardName()).isEqualTo("volatility_guard");
  }

  @Test
  @DisplayName("order is 60")
  void should_returnOrder60() {
    assertThat(guard.order()).isEqualTo(60);
  }

  @Nested
  @DisplayName("when volatility guard enabled")
  class Enabled {

    @Test
    @DisplayName("blocks when reversals exceed max threshold")
    void should_block_when_reversalsExceedMax() {
      PricingSignalSet signals = signalsWithReversals(5);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isFalse();
      assertThat(result.reason()).isEqualTo("pricing.guard.volatility");
      assertThat(result.args()).containsKey("count");
      assertThat(result.args()).containsKey("maxReversals");
    }

    @Test
    @DisplayName("blocks when reversals equal max threshold")
    void should_block_when_reversalsEqualMax() {
      PricingSignalSet signals = signalsWithReversals(3);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isFalse();
    }

    @Test
    @DisplayName("passes when reversals below max threshold")
    void should_pass_when_reversalsBelowMax() {
      PricingSignalSet signals = signalsWithReversals(2);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("passes when reversals is null")
    void should_pass_when_reversalsNull() {
      PricingSignalSet signals = signalsWithReversals(null);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
    }
  }

  @Nested
  @DisplayName("when volatility guard disabled")
  class Disabled {

    @Test
    @DisplayName("passes even with excessive reversals")
    void should_pass_when_guardDisabled() {
      PricingSignalSet signals = signalsWithReversals(100);
      GuardConfig config = new GuardConfig(
          null, null, null, false, null, null, null, null, null);

      GuardResult result = guard.check(signals, BigDecimal.TEN, config);

      assertThat(result.passed()).isTrue();
    }
  }

  @Nested
  @DisplayName("custom thresholds")
  class CustomThresholds {

    @Test
    @DisplayName("respects custom reversals threshold")
    void should_useCustomMax_when_configured() {
      PricingSignalSet signals = signalsWithReversals(5);
      GuardConfig config = new GuardConfig(
          null, null, null, true, 10, 7, null, null, null);

      GuardResult result = guard.check(signals, BigDecimal.TEN, config);

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("blocks at exact custom threshold")
    void should_block_when_atCustomThreshold() {
      PricingSignalSet signals = signalsWithReversals(10);
      GuardConfig config = new GuardConfig(
          null, null, null, true, 10, 7, null, null, null);

      GuardResult result = guard.check(signals, BigDecimal.TEN, config);

      assertThat(result.passed()).isFalse();
    }
  }

  @Nested
  @DisplayName("edge cases")
  class EdgeCases {

    @Test
    @DisplayName("passes when reversals is zero")
    void should_pass_when_reversalsZero() {
      PricingSignalSet signals = signalsWithReversals(0);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("passes when reversals is 1 (well below default of 3)")
    void should_pass_when_reversalsOne() {
      PricingSignalSet signals = signalsWithReversals(1);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
    }
  }

  private PricingSignalSet signalsWithReversals(Integer reversals) {
    return new PricingSignalSet(
        new BigDecimal("1000"), null, null, null,
        false, false,
        null, null, null, null, null, reversals, null, null);
  }
}
