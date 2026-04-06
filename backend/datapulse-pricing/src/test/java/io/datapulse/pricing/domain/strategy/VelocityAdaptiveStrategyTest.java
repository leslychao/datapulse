package io.datapulse.pricing.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.pricing.domain.ExecutionMode;
import io.datapulse.pricing.domain.PolicySnapshot;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.domain.PricingSignalSet;
import io.datapulse.pricing.domain.StrategyResult;

class VelocityAdaptiveStrategyTest {

  private VelocityAdaptiveStrategy strategy;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    strategy = new VelocityAdaptiveStrategy(objectMapper);
  }

  @Test
  @DisplayName("strategyType returns VELOCITY_ADAPTIVE")
  void should_returnVelocityAdaptive_when_callingStrategyType() {
    assertThat(strategy.strategyType()).isEqualTo(PolicyType.VELOCITY_ADAPTIVE);
  }

  @Nested
  @DisplayName("deceleration — price decrease")
  class Deceleration {

    @Test
    @DisplayName("reduces price when velocity ratio below deceleration threshold")
    void should_reducePrice_when_velocityDecelerating() {
      // velocityShort=2.1, velocityLong=3.5 → ratio=0.60 < 0.70
      // deviation = (0.70 - 0.60) / 0.70 = 0.1429
      // adjustment = -0.05 × 0.1429 = -0.007143
      // raw = 1000 × (1 - 0.007143) = 992.86
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("2.1"))
          .salesVelocityLong(new BigDecimal("3.5"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNotNull();
      assertThat(result.rawTargetPrice().compareTo(new BigDecimal("1000"))).isNegative();
      assertThat(result.reasonKey()).isNull();
      assertThat(result.explanation()).contains("ratio=0.60");
      assertThat(result.explanation()).contains("adjustment=");
    }

    @Test
    @DisplayName("applies max deceleration discount when ratio is zero")
    void should_applyMaxDiscount_when_velocityZero() {
      // velocityShort=0, velocityLong=3.5 → ratio=0
      // deviation = (0.70 - 0) / 0.70 = 1.0 → capped at 1.0
      // adjustment = -0.05 × 1.0 = -0.05
      // raw = 1000 × 0.95 = 950.00
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(BigDecimal.ZERO)
          .salesVelocityLong(new BigDecimal("3.5"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("950.00"));
    }

    @Test
    @DisplayName("caps deviation at 1.0 for extreme deceleration")
    void should_capDeviation_when_extremeDeceleration() {
      // velocityShort=0.01, velocityLong=5.0 → ratio≈0.002 << 0.70
      // deviation would be (0.70 - 0.002) / 0.70 ≈ 0.997, capped at 1.0 anyway
      // adjustment = -0.05 × 1.0 = -0.05
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("2000"))
          .salesVelocityShort(new BigDecimal("0.01"))
          .salesVelocityLong(new BigDecimal("5.0"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("1900.00"));
    }

    @Test
    @DisplayName("uses custom deceleration parameters")
    void should_useCustomParams_when_customDecelerationProvided() {
      // decelThreshold=0.80, discountPct=0.10
      // velocityShort=2.0, velocityLong=5.0 → ratio=0.40 < 0.80
      // deviation = (0.80 - 0.40) / 0.80 = 0.50
      // adjustment = -0.10 × 0.50 = -0.05
      // raw = 1000 × 0.95 = 950.00
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("2.0"))
          .salesVelocityLong(new BigDecimal("5.0"))
          .build();

      String params = """
          {
            "decelerationThreshold": 0.80,
            "decelerationDiscountPct": 0.10,
            "accelerationThreshold": 1.30,
            "accelerationMarkupPct": 0.03
          }
          """;

      StrategyResult result = strategy.calculate(signals, policySnapshot(params));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("950.00"));
    }
  }

  @Nested
  @DisplayName("acceleration — price increase")
  class Acceleration {

    @Test
    @DisplayName("increases price when velocity ratio above acceleration threshold")
    void should_increasePrice_when_velocityAccelerating() {
      // velocityShort=5.0, velocityLong=3.0 → ratio=1.667 > 1.30
      // deviation = (1.667 - 1.30) / 1.30 = 0.2821
      // adjustment = +0.03 × 0.2821 = +0.00846
      // raw = 1000 × 1.00846 = 1008.46
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("5.0"))
          .salesVelocityLong(new BigDecimal("3.0"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNotNull();
      assertThat(result.rawTargetPrice().compareTo(new BigDecimal("1000"))).isPositive();
      assertThat(result.reasonKey()).isNull();
    }

    @Test
    @DisplayName("caps acceleration deviation at 1.0")
    void should_capAcceleration_when_extremeAcceleration() {
      // velocityShort=10.0, velocityLong=2.0 → ratio=5.0 >> 1.30
      // deviation = (5.0 - 1.30) / 1.30 = 2.846, capped at 1.0
      // adjustment = +0.03 × 1.0 = +0.03
      // raw = 1000 × 1.03 = 1030.00
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("10.0"))
          .salesVelocityLong(new BigDecimal("2.0"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("1030.00"));
    }

    @Test
    @DisplayName("uses custom acceleration parameters")
    void should_useCustomParams_when_customAccelerationProvided() {
      // accelThreshold=1.20, markupPct=0.08
      // velocityShort=6.0, velocityLong=3.0 → ratio=2.0 > 1.20
      // deviation = (2.0 - 1.20) / 1.20 = 0.6667
      // adjustment = +0.08 × 0.6667 = +0.05333
      // raw = 1000 × 1.05333 = 1053.33
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("6.0"))
          .salesVelocityLong(new BigDecimal("3.0"))
          .build();

      String params = """
          {
            "decelerationThreshold": 0.70,
            "decelerationDiscountPct": 0.05,
            "accelerationThreshold": 1.20,
            "accelerationMarkupPct": 0.08
          }
          """;

      StrategyResult result = strategy.calculate(signals, policySnapshot(params));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("1053.33"));
    }
  }

  @Nested
  @DisplayName("stable — HOLD")
  class Stable {

    @Test
    @DisplayName("skips with STABLE reason when ratio within thresholds")
    void should_skip_when_velocityStable() {
      // velocityShort=3.5, velocityLong=3.5 → ratio=1.0 ∈ [0.70, 1.30]
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("3.5"))
          .salesVelocityLong(new BigDecimal("3.5"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.velocity.stable");
      assertThat(result.explanation()).contains("stable");
    }

    @Test
    @DisplayName("skips when ratio exactly equals deceleration threshold")
    void should_skip_when_ratioEqualsDecelerationThreshold() {
      // velocityShort=2.45, velocityLong=3.5 → ratio=0.70 == decelThreshold
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("2.45"))
          .salesVelocityLong(new BigDecimal("3.5"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.velocity.stable");
    }

    @Test
    @DisplayName("skips when ratio exactly equals acceleration threshold")
    void should_skip_when_ratioEqualsAccelerationThreshold() {
      // velocityShort=4.55, velocityLong=3.5 → ratio=1.30 == accelThreshold
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("4.55"))
          .salesVelocityLong(new BigDecimal("3.5"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.velocity.stable");
    }
  }

  @Nested
  @DisplayName("insufficient data — SKIP")
  class InsufficientData {

    @Test
    @DisplayName("skips when salesVelocityLong is null")
    void should_skip_when_velocityLongNull() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("3.0"))
          .salesVelocityLong(null)
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.velocity.insufficient_data");
    }

    @Test
    @DisplayName("skips when salesVelocityLong is zero")
    void should_skip_when_velocityLongZero() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("3.0"))
          .salesVelocityLong(BigDecimal.ZERO)
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.velocity.insufficient_data");
    }

    @Test
    @DisplayName("skips when salesVelocityLong is negative")
    void should_skip_when_velocityLongNegative() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("3.0"))
          .salesVelocityLong(new BigDecimal("-1.0"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.velocity.insufficient_data");
    }

    @Test
    @DisplayName("skips when baseline units below minBaselineSales")
    void should_skip_when_baselineBelowMinimum() {
      // velocityLong = 0.2 u/d × 30d = 6 units < minBaselineSales (10)
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("0.1"))
          .salesVelocityLong(new BigDecimal("0.2"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.velocity.insufficient_data");
      assertThat(result.explanation()).contains("Baseline sales");
    }

    @Test
    @DisplayName("skips when currentPrice is null")
    void should_skip_when_currentPriceNull() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(null)
          .salesVelocityShort(new BigDecimal("3.0"))
          .salesVelocityLong(new BigDecimal("3.5"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.strategy.current_price_missing");
    }

    @Test
    @DisplayName("skips when currentPrice is zero")
    void should_skip_when_currentPriceZero() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(BigDecimal.ZERO)
          .salesVelocityShort(new BigDecimal("3.0"))
          .salesVelocityLong(new BigDecimal("3.5"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.strategy.current_price_missing");
    }
  }

  @Nested
  @DisplayName("edge cases")
  class EdgeCases {

    @Test
    @DisplayName("treats null velocityShort as zero")
    void should_treatNullShortAsZero_when_velocityShortNull() {
      // velocityShort=null → 0, velocityLong=3.5 → ratio=0
      // deviation = (0.70 - 0) / 0.70 = 1.0
      // adjustment = -0.05 × 1.0 = -0.05
      // raw = 1000 × 0.95 = 950
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(null)
          .salesVelocityLong(new BigDecimal("3.5"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("950.00"));
    }

    @Test
    @DisplayName("baseline exactly equals minBaselineSales → proceeds")
    void should_proceed_when_baselineExactlyAtMinimum() {
      // velocityLong = 10/30 = 0.3333 u/d × 30d = 10.0 == minBaselineSales
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("0.15"))
          .salesVelocityLong(new BigDecimal("0.3333"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.reasonKey())
          .isNotEqualTo("pricing.velocity.insufficient_data");
    }

    @Test
    @DisplayName("uses custom minBaselineSales")
    void should_useCustomMin_when_minBaselineSalesProvided() {
      // velocityLong = 0.2 u/d × 30d = 6 units
      // default min = 10 → skip, but custom min = 5 → proceed
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("0.1"))
          .salesVelocityLong(new BigDecimal("0.2"))
          .build();

      String params = """
          {
            "minBaselineSales": 5
          }
          """;

      StrategyResult result = strategy.calculate(signals, policySnapshot(params));

      assertThat(result.reasonKey())
          .isNotEqualTo("pricing.velocity.insufficient_data");
    }

    @Test
    @DisplayName("produces correct explanation format")
    void should_produceExplanation_when_decelerating() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("2.3"))
          .salesVelocityLong(new BigDecimal("3.5"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.explanation())
          .contains("velocity_short=2.3 u/d (7d)")
          .contains("velocity_long=3.5 u/d (30d)")
          .contains("ratio=")
          .contains("adjustment=")
          .contains("raw=");
    }
  }

  @Nested
  @DisplayName("invalid params")
  class InvalidParams {

    @Test
    @DisplayName("throws IllegalStateException on invalid JSON params")
    void should_throwIllegalState_when_invalidJson() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("3.0"))
          .salesVelocityLong(new BigDecimal("3.5"))
          .build();

      PolicySnapshot policy = policySnapshot("not-a-json");

      assertThatThrownBy(() -> strategy.calculate(signals, policy))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Invalid VELOCITY_ADAPTIVE strategy_params JSON");
    }
  }

  @Nested
  @DisplayName("default params — all null")
  class DefaultParams {

    @Test
    @DisplayName("works with empty JSON object (all defaults)")
    void should_useDefaults_when_emptyParams() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .salesVelocityShort(new BigDecimal("1.0"))
          .salesVelocityLong(new BigDecimal("3.5"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot("{}"));

      assertThat(result.rawTargetPrice()).isNotNull();
      assertThat(result.rawTargetPrice().compareTo(new BigDecimal("1000"))).isNegative();
    }
  }

  // --- helpers ---

  private PolicySnapshot policySnapshot(String strategyParams) {
    return new PolicySnapshot(
        1L, 1, "Velocity Test", PolicyType.VELOCITY_ADAPTIVE, strategyParams,
        null, null, null, null, null, ExecutionMode.RECOMMENDATION);
  }

  private String defaultParams() {
    return """
        {
          "decelerationThreshold": 0.70,
          "accelerationThreshold": 1.30,
          "decelerationDiscountPct": 0.05,
          "accelerationMarkupPct": 0.03,
          "minBaselineSales": 10
        }
        """;
  }

  private static SignalSetBuilder signalsBuilder() {
    return new SignalSetBuilder();
  }

  static class SignalSetBuilder {

    private BigDecimal currentPrice;
    private BigDecimal salesVelocityShort;
    private BigDecimal salesVelocityLong;

    SignalSetBuilder currentPrice(BigDecimal v) {
      this.currentPrice = v;
      return this;
    }

    SignalSetBuilder salesVelocityShort(BigDecimal v) {
      this.salesVelocityShort = v;
      return this;
    }

    SignalSetBuilder salesVelocityLong(BigDecimal v) {
      this.salesVelocityLong = v;
      return this;
    }

    PricingSignalSet build() {
      return new PricingSignalSet(
          currentPrice, null, null, null, false, false,
          null, null, null, null, null, null, null, null,
          salesVelocityShort, salesVelocityLong, null,
          null, null,
          null, null, null);
    }
  }
}
