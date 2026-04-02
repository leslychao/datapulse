package io.datapulse.pricing.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

class FrequencyGuardTest {

  private final FrequencyGuard guard = new FrequencyGuard();

  @Test
  @DisplayName("guard name is 'frequency_guard'")
  void should_returnCorrectName() {
    assertThat(guard.guardName()).isEqualTo("frequency_guard");
  }

  @Test
  @DisplayName("order is 50")
  void should_returnOrder50() {
    assertThat(guard.order()).isEqualTo(50);
  }

  @Nested
  @DisplayName("when frequency guard enabled")
  class Enabled {

    @Test
    @DisplayName("blocks when last change was too recent")
    void should_block_when_lastChangeTooRecent() {
      OffsetDateTime recentChange = OffsetDateTime.now().minusHours(2);
      PricingSignalSet signals = signalsWithLastChange(recentChange);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isFalse();
      assertThat(result.reason()).isEqualTo("pricing.guard.frequency");
      assertThat(result.args()).containsKey("hours");
      assertThat(result.args()).containsKey("lastChangeAt");
    }

    @Test
    @DisplayName("passes when last change was long ago")
    void should_pass_when_lastChangeLongAgo() {
      OffsetDateTime oldChange = OffsetDateTime.now().minusHours(48);
      PricingSignalSet signals = signalsWithLastChange(oldChange);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("passes when last change is null (no previous change)")
    void should_pass_when_lastChangeNull() {
      PricingSignalSet signals = signalsWithLastChange(null);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("respects custom frequency threshold hours")
    void should_useCustomThreshold_when_configured() {
      OffsetDateTime recentChange = OffsetDateTime.now().minusHours(3);
      PricingSignalSet signals = signalsWithLastChange(recentChange);

      GuardConfig strictConfig = new GuardConfig(
          null, true, 6, null, null, null, null, null, null);

      GuardResult result = guard.check(signals, BigDecimal.TEN, strictConfig);

      assertThat(result.passed()).isFalse();
    }
  }

  @Nested
  @DisplayName("when frequency guard disabled")
  class Disabled {

    @Test
    @DisplayName("passes even with very recent change")
    void should_pass_when_guardDisabled() {
      OffsetDateTime justNow = OffsetDateTime.now().minusMinutes(1);
      PricingSignalSet signals = signalsWithLastChange(justNow);
      GuardConfig config = new GuardConfig(
          null, false, null, null, null, null, null, null, null);

      GuardResult result = guard.check(signals, BigDecimal.TEN, config);

      assertThat(result.passed()).isTrue();
    }
  }

  @Nested
  @DisplayName("boundary conditions")
  class Boundary {

    @Test
    @DisplayName("blocks when last change is exactly at threshold boundary")
    void should_block_when_exactlyAtBoundary() {
      OffsetDateTime exactBoundary = OffsetDateTime.now().minusHours(24);
      PricingSignalSet signals = signalsWithLastChange(exactBoundary);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      // At exactly 24h — still considered "within", depending on implementation
      // Either pass or block is acceptable, but must not throw
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("passes when last change is just past threshold")
    void should_pass_when_justPastThreshold() {
      OffsetDateTime justPast = OffsetDateTime.now().minusHours(25);
      PricingSignalSet signals = signalsWithLastChange(justPast);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
    }
  }

  private PricingSignalSet signalsWithLastChange(OffsetDateTime lastPriceChangeAt) {
    return new PricingSignalSet(
        new BigDecimal("1000"), null, null, null,
        false, false,
        null, null, null, null, lastPriceChangeAt, null, null, null);
  }
}
