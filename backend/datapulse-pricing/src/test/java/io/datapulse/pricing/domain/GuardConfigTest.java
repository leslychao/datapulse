package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GuardConfigTest {

  @Nested
  @DisplayName("DEFAULTS")
  class Defaults {

    @Test
    @DisplayName("default config has all guards enabled except ad_cost (disabled by default)")
    void should_haveAllGuardsEnabled_when_defaults() {
      GuardConfig config = GuardConfig.DEFAULTS;

      assertThat(config.isMarginGuardEnabled()).isTrue();
      assertThat(config.isFrequencyGuardEnabled()).isTrue();
      assertThat(config.isVolatilityGuardEnabled()).isTrue();
      assertThat(config.isPromoGuardEnabled()).isTrue();
      assertThat(config.isStockOutGuardEnabled()).isTrue();
      assertThat(config.isAdCostGuardEnabled()).isFalse();
    }

    @Test
    @DisplayName("default config has correct threshold values")
    void should_haveCorrectThresholds_when_defaults() {
      GuardConfig config = GuardConfig.DEFAULTS;

      assertThat(config.effectiveFrequencyGuardHours()).isEqualTo(24);
      assertThat(config.effectiveVolatilityReversals()).isEqualTo(3);
      assertThat(config.effectiveVolatilityPeriodDays()).isEqualTo(7);
      assertThat(config.effectiveStaleDataGuardHours()).isEqualTo(24);
    }
  }

  @Nested
  @DisplayName("effective methods with null values")
  class NullFallbacks {

    @Test
    @DisplayName("null boolean flags default to enabled (true) except ad_cost which defaults to disabled")
    void should_defaultToEnabled_when_nullFlags() {
      GuardConfig config = new GuardConfig(
          null, null, null, null, null, null, null, null, null, null, null);

      assertThat(config.isMarginGuardEnabled()).isTrue();
      assertThat(config.isFrequencyGuardEnabled()).isTrue();
      assertThat(config.isVolatilityGuardEnabled()).isTrue();
      assertThat(config.isPromoGuardEnabled()).isTrue();
      assertThat(config.isStockOutGuardEnabled()).isTrue();
      assertThat(config.isAdCostGuardEnabled()).isFalse();
    }

    @Test
    @DisplayName("null thresholds fall back to defaults")
    void should_fallbackToDefaults_when_nullThresholds() {
      GuardConfig config = new GuardConfig(
          null, null, null, null, null, null, null, null, null, null, null);

      assertThat(config.effectiveFrequencyGuardHours()).isEqualTo(24);
      assertThat(config.effectiveVolatilityReversals()).isEqualTo(3);
      assertThat(config.effectiveVolatilityPeriodDays()).isEqualTo(7);
      assertThat(config.effectiveStaleDataGuardHours()).isEqualTo(24);
      assertThat(config.effectiveAdCostDrrThreshold()).isEqualByComparingTo("0.15");
    }
  }

  @Nested
  @DisplayName("explicit values override defaults")
  class ExplicitValues {

    @Test
    @DisplayName("explicit false disables guards")
    void should_disableGuards_when_explicitFalse() {
      GuardConfig config = new GuardConfig(
          false, false, null, false, null, null, false, false, null, false, null);

      assertThat(config.isMarginGuardEnabled()).isFalse();
      assertThat(config.isFrequencyGuardEnabled()).isFalse();
      assertThat(config.isVolatilityGuardEnabled()).isFalse();
      assertThat(config.isPromoGuardEnabled()).isFalse();
      assertThat(config.isStockOutGuardEnabled()).isFalse();
      assertThat(config.isAdCostGuardEnabled()).isFalse();
    }

    @Test
    @DisplayName("custom thresholds override defaults")
    void should_useCustomValues_when_provided() {
      GuardConfig config = new GuardConfig(
          null, null, 12, null, 5, 14, null, null, 48, null, null);

      assertThat(config.effectiveFrequencyGuardHours()).isEqualTo(12);
      assertThat(config.effectiveVolatilityReversals()).isEqualTo(5);
      assertThat(config.effectiveVolatilityPeriodDays()).isEqualTo(14);
      assertThat(config.effectiveStaleDataGuardHours()).isEqualTo(48);
    }
  }
}
