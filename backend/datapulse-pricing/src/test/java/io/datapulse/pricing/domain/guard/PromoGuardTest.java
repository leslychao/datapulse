package io.datapulse.pricing.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

class PromoGuardTest {

  private final PromoGuard guard = new PromoGuard();

  @Test
  @DisplayName("guard name is 'promo_guard'")
  void should_returnCorrectName() {
    assertThat(guard.guardName()).isEqualTo("promo_guard");
  }

  @Test
  @DisplayName("order is 35")
  void should_returnOrder35() {
    assertThat(guard.order()).isEqualTo(35);
  }

  @Nested
  @DisplayName("when promo guard enabled")
  class Enabled {

    @Test
    @DisplayName("blocks when promo is active")
    void should_block_when_promoActive() {
      PricingSignalSet signals = signals(true);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isFalse();
      assertThat(result.reason()).isEqualTo("pricing.guard.promo_active");
    }

    @Test
    @DisplayName("passes when promo is not active")
    void should_pass_when_promoNotActive() {
      PricingSignalSet signals = signals(false);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
    }
  }

  @Nested
  @DisplayName("when promo guard disabled")
  class Disabled {

    @Test
    @DisplayName("passes even when promo is active")
    void should_pass_when_guardDisabled() {
      PricingSignalSet signals = signals(true);
      GuardConfig config = disabledConfig();

      GuardResult result = guard.check(signals, BigDecimal.TEN, config);

      assertThat(result.passed()).isTrue();
    }
  }

  private PricingSignalSet signals(boolean promoActive) {
    return new PricingSignalSet(
        new BigDecimal("1000"), null, null, null,
        false, promoActive,
        null, null, null, null, null, null, null);
  }

  private GuardConfig disabledConfig() {
    return new GuardConfig(null, null, null, null, null, null, false, null, null);
  }
}
