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

class PriceCorridorStrategyTest {

  private PriceCorridorStrategy strategy;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    strategy = new PriceCorridorStrategy(objectMapper);
  }

  @Test
  @DisplayName("strategyType returns PRICE_CORRIDOR")
  void should_returnPriceCorridor_when_callingStrategyType() {
    assertThat(strategy.strategyType()).isEqualTo(PolicyType.PRICE_CORRIDOR);
  }

  @Nested
  @DisplayName("price within corridor")
  class WithinCorridor {

    @Test
    @DisplayName("returns current price unchanged when within corridor")
    void should_returnCurrentPrice_when_withinCorridor() {
      PricingSignalSet signals = signals(new BigDecimal("500"));
      PolicySnapshot policy = corridorPolicy(new BigDecimal("100"), new BigDecimal("1000"));

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isEqualByComparingTo(new BigDecimal("500"));
      assertThat(result.explanation()).contains("within corridor");
      assertThat(result.reasonKey()).isNull();
    }

    @Test
    @DisplayName("returns current price when exactly at min boundary")
    void should_returnCurrentPrice_when_exactlyAtMin() {
      PricingSignalSet signals = signals(new BigDecimal("100"));
      PolicySnapshot policy = corridorPolicy(new BigDecimal("100"), new BigDecimal("1000"));

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isEqualByComparingTo(new BigDecimal("100"));
      assertThat(result.explanation()).contains("within corridor");
    }

    @Test
    @DisplayName("returns current price when exactly at max boundary")
    void should_returnCurrentPrice_when_exactlyAtMax() {
      PricingSignalSet signals = signals(new BigDecimal("1000"));
      PolicySnapshot policy = corridorPolicy(new BigDecimal("100"), new BigDecimal("1000"));

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isEqualByComparingTo(new BigDecimal("1000"));
      assertThat(result.explanation()).contains("within corridor");
    }
  }

  @Nested
  @DisplayName("price below min")
  class BelowMin {

    @Test
    @DisplayName("clamps to min price when current is below corridor min")
    void should_clampToMin_when_currentBelowMin() {
      PricingSignalSet signals = signals(new BigDecimal("50"));
      PolicySnapshot policy = corridorPolicy(new BigDecimal("100"), new BigDecimal("1000"));

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isEqualByComparingTo(new BigDecimal("100"));
      assertThat(result.explanation()).contains("corridor min");
    }
  }

  @Nested
  @DisplayName("price above max")
  class AboveMax {

    @Test
    @DisplayName("clamps to max price when current is above corridor max")
    void should_clampToMax_when_currentAboveMax() {
      PricingSignalSet signals = signals(new BigDecimal("1500"));
      PolicySnapshot policy = corridorPolicy(new BigDecimal("100"), new BigDecimal("1000"));

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isEqualByComparingTo(new BigDecimal("1000"));
      assertThat(result.explanation()).contains("corridor max");
    }
  }

  @Nested
  @DisplayName("null boundaries")
  class NullBoundaries {

    @Test
    @DisplayName("allows any low price when min is null")
    void should_allowLowPrice_when_minIsNull() {
      PricingSignalSet signals = signals(new BigDecimal("1"));
      PolicySnapshot policy = corridorPolicy(null, new BigDecimal("1000"));

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isEqualByComparingTo(new BigDecimal("1"));
      assertThat(result.explanation()).contains("within corridor");
    }

    @Test
    @DisplayName("allows any high price when max is null")
    void should_allowHighPrice_when_maxIsNull() {
      PricingSignalSet signals = signals(new BigDecimal("99999"));
      PolicySnapshot policy = corridorPolicy(new BigDecimal("100"), null);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isEqualByComparingTo(new BigDecimal("99999"));
      assertThat(result.explanation()).contains("within corridor");
    }
  }

  @Nested
  @DisplayName("missing current price")
  class MissingCurrentPrice {

    @Test
    @DisplayName("skips when current price is null")
    void should_skip_when_currentPriceIsNull() {
      PricingSignalSet signals = signals(null);
      PolicySnapshot policy = corridorPolicy(new BigDecimal("100"), new BigDecimal("1000"));

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.strategy.current_price_missing");
    }
  }

  @Nested
  @DisplayName("invalid params")
  class InvalidParams {

    @Test
    @DisplayName("throws IllegalStateException on invalid JSON params")
    void should_throwIllegalState_when_invalidJson() {
      PricingSignalSet signals = signals(new BigDecimal("500"));
      PolicySnapshot policy = new PolicySnapshot(
          1L, 1, "Test", PolicyType.PRICE_CORRIDOR, "{bad json",
          null, null, null, null, null, ExecutionMode.RECOMMENDATION);

      assertThatThrownBy(() -> strategy.calculate(signals, policy))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Invalid PRICE_CORRIDOR strategy_params JSON");
    }
  }

  @Nested
  @DisplayName("edge cases")
  class EdgeCases {

    @Test
    @DisplayName("returns current price when both min and max are null (open corridor)")
    void should_returnCurrentPrice_when_bothBoundsNull() {
      PricingSignalSet signals = signals(new BigDecimal("500"));
      PolicySnapshot policy = corridorPolicy(null, null);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    @DisplayName("handles min > max gracefully (invalid config)")
    void should_handle_when_minGreaterThanMax() {
      PricingSignalSet signals = signals(new BigDecimal("500"));
      PolicySnapshot policy = corridorPolicy(new BigDecimal("1000"), new BigDecimal("100"));

      // min=1000, max=100, price=500 → below min=1000 → clamp up to 1000
      // but also above max=100 → depends on order
      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isNotNull();
    }

    @Test
    @DisplayName("works with very small prices (precision)")
    void should_handle_when_verySmallPrices() {
      PricingSignalSet signals = signals(new BigDecimal("0.50"));
      PolicySnapshot policy = corridorPolicy(new BigDecimal("1.00"), new BigDecimal("10.00"));

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isEqualByComparingTo(new BigDecimal("1.00"));
    }
  }

  // --- helpers ---

  private PricingSignalSet signals(BigDecimal currentPrice) {
    return new PricingSignalSet(
        currentPrice, null, null, null, false, false,
        null, null, null, null, null, null, null, null);
  }

  private PolicySnapshot corridorPolicy(BigDecimal minPrice, BigDecimal maxPrice) {
    String params = """
        {"minPrice": %s, "maxPrice": %s}
        """.formatted(
        minPrice != null ? minPrice.toPlainString() : "null",
        maxPrice != null ? maxPrice.toPlainString() : "null");
    return new PolicySnapshot(
        1L, 1, "Corridor Policy", PolicyType.PRICE_CORRIDOR, params,
        null, null, null, null, null, ExecutionMode.RECOMMENDATION);
  }
}
