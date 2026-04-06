package io.datapulse.pricing.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

class ManualLockGuardTest {

  private final ManualLockGuard guard = new ManualLockGuard();

  @Test
  @DisplayName("guard name is 'manual_lock_guard'")
  void should_returnCorrectName() {
    assertThat(guard.guardName()).isEqualTo("manual_lock_guard");
  }

  @Test
  @DisplayName("order is 10 (runs early)")
  void should_returnOrder10() {
    assertThat(guard.order()).isEqualTo(10);
  }

  @Nested
  @DisplayName("check")
  class Check {

    @Test
    @DisplayName("blocks when manual lock is active")
    void should_block_when_manualLockActive() {
      PricingSignalSet signals = signals(true);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isFalse();
      assertThat(result.guardName()).isEqualTo("manual_lock_guard");
      assertThat(result.reason()).isEqualTo("pricing.guard.manual_lock");
    }

    @Test
    @DisplayName("passes when manual lock is not active")
    void should_pass_when_manualLockNotActive() {
      PricingSignalSet signals = signals(false);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
      assertThat(result.guardName()).isEqualTo("manual_lock_guard");
      assertThat(result.reason()).isNull();
    }
  }

  private PricingSignalSet signals(boolean manualLockActive) {
    return new PricingSignalSet(
        new BigDecimal("1000"), null, null, null,
        manualLockActive, false,
        null, null, null, null, null, null, null, null,
        null, null, null,
        null, null,
        null, null, null);
  }
}
