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

class StockBalancingStrategyTest {

  private StockBalancingStrategy strategy;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    strategy = new StockBalancingStrategy(objectMapper);
  }

  @Test
  @DisplayName("strategyType returns STOCK_BALANCING")
  void should_returnStockBalancing_when_callingStrategyType() {
    assertThat(strategy.strategyType()).isEqualTo(PolicyType.STOCK_BALANCING);
  }

  @Nested
  @DisplayName("near-stockout — price markup")
  class NearStockout {

    @Test
    @DisplayName("increases price when daysOfCover below critical threshold")
    void should_markupPrice_when_nearStockout() {
      // daysOfCover=3 < criticalDaysOfCover=7
      // adjustment = +0.05 (stockoutMarkupPct)
      // raw = 1000 × 1.05 = 1050.00
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .daysOfCover(new BigDecimal("3"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("1050.00"));
      assertThat(result.reasonKey()).isNull();
      assertThat(result.explanation()).contains("days_of_cover=3.0");
      assertThat(result.explanation()).contains("critical_threshold=7");
      assertThat(result.explanation()).contains("lead_time=14");
      assertThat(result.explanation()).contains("adjustment=+5.0%");
    }

    @Test
    @DisplayName("applies custom stockout markup")
    void should_useCustomMarkup_when_customStockoutMarkupProvided() {
      // stockoutMarkupPct=0.15 → 15% markup
      // raw = 2000 × 1.15 = 2300.00
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("2000"))
          .daysOfCover(new BigDecimal("2"))
          .build();

      String params = """
          {
            "criticalDaysOfCover": 7,
            "overstockDaysOfCover": 60,
            "stockoutMarkupPct": 0.15
          }
          """;

      StrategyResult result = strategy.calculate(signals, policySnapshot(params));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("2300.00"));
    }

    @Test
    @DisplayName("marks up when daysOfCover is zero")
    void should_markup_when_daysOfCoverZero() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("500"))
          .daysOfCover(BigDecimal.ZERO)
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("525.00"));
      assertThat(result.reasonKey()).isNull();
    }
  }

  @Nested
  @DisplayName("overstock — progressive discount")
  class Overstock {

    @Test
    @DisplayName("applies progressive discount when days_of_cover exceeds overstock threshold")
    void should_discountPrice_when_overstocked() {
      // daysOfCover=85 > overstockDaysOfCover=60
      // overshoot = (85 - 60) / 60 = 0.4167
      // discount = 0.4167 × 0.10 = 0.04167
      // adjustment = -0.04167
      // raw = 5000 × (1 - 0.04167) = 5000 × 0.95833 = 4791.65
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("5000"))
          .daysOfCover(new BigDecimal("85"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNotNull();
      assertThat(result.rawTargetPrice().compareTo(new BigDecimal("5000"))).isNegative();
      assertThat(result.rawTargetPrice().compareTo(new BigDecimal("4000"))).isPositive();
      assertThat(result.reasonKey()).isNull();
      assertThat(result.explanation()).contains("overstock_threshold=60");
      assertThat(result.explanation()).contains("discount_factor=0.10");
    }

    @Test
    @DisplayName("progressive discount increases with larger overshoot")
    void should_increaseDiscount_when_largerOvershoot() {
      // daysOfCover=120 > overstockDaysOfCover=60
      // overshoot = (120 - 60) / 60 = 1.0
      // discount = 1.0 × 0.10 = 0.10
      // raw = 1000 × 0.90 = 900.00
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .daysOfCover(new BigDecimal("120"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("900.00"));
    }

    @Test
    @DisplayName("uses custom discount factor and overstock threshold")
    void should_useCustomParams_when_customOverstockProvided() {
      // overstockDaysOfCover=90, discountFactor=0.20
      // daysOfCover=180 → overshoot = (180-90)/90 = 1.0
      // discount = 1.0 × 0.20 = 0.20
      // raw = 1000 × 0.80 = 800.00
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .daysOfCover(new BigDecimal("180"))
          .build();

      String params = """
          {
            "criticalDaysOfCover": 5,
            "overstockDaysOfCover": 90,
            "overstockDiscountFactor": 0.20,
            "maxDiscountPct": 0.30
          }
          """;

      StrategyResult result = strategy.calculate(signals, policySnapshot(params));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("800.00"));
    }
  }

  @Nested
  @DisplayName("extreme overstock — clamp at maxDiscountPct")
  class ExtremeOverstock {

    @Test
    @DisplayName("clamps discount at maxDiscountPct for extreme overshoot")
    void should_clampDiscount_when_extremeOverstock() {
      // daysOfCover=500 > overstockDaysOfCover=60
      // overshoot = (500 - 60) / 60 = 7.333
      // raw discount = 7.333 × 0.10 = 0.7333, but clamped at maxDiscountPct=0.20
      // adjustment = -0.20
      // raw = 1000 × 0.80 = 800.00
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .daysOfCover(new BigDecimal("500"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("800.00"));
    }

    @Test
    @DisplayName("clamps at custom maxDiscountPct")
    void should_clampAtCustomMax_when_customMaxDiscountPct() {
      // maxDiscountPct=0.10 → clamped at 10%
      // daysOfCover=300 → overshoot = (300-60)/60 = 4.0
      // raw discount = 4.0 × 0.10 = 0.40, clamped at 0.10
      // raw = 2000 × 0.90 = 1800.00
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("2000"))
          .daysOfCover(new BigDecimal("300"))
          .build();

      String params = """
          {
            "maxDiscountPct": 0.10
          }
          """;

      StrategyResult result = strategy.calculate(signals, policySnapshot(params));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("1800.00"));
    }
  }

  @Nested
  @DisplayName("normal level — HOLD")
  class NormalLevel {

    @Test
    @DisplayName("skips with NORMAL reason when daysOfCover within thresholds")
    void should_skip_when_stockLevelNormal() {
      // daysOfCover=30, criticalDaysOfCover=7, overstockDaysOfCover=60 → normal
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .daysOfCover(new BigDecimal("30"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.stock.normal");
      assertThat(result.explanation()).contains("normal");
    }

    @Test
    @DisplayName("skips when daysOfCover exactly equals critical threshold")
    void should_skip_when_daysOfCoverEqualsCritical() {
      // daysOfCover=7 == criticalDaysOfCover=7 → not < critical, so normal
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .daysOfCover(new BigDecimal("7"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.stock.normal");
    }

    @Test
    @DisplayName("skips when daysOfCover exactly equals overstock threshold")
    void should_skip_when_daysOfCoverEqualsOverstock() {
      // daysOfCover=60 == overstockDaysOfCover=60 → not > overstock, so normal
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .daysOfCover(new BigDecimal("60"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.stock.normal");
    }
  }

  @Nested
  @DisplayName("no inventory data — SKIP")
  class NoInventoryData {

    @Test
    @DisplayName("skips when daysOfCover is null")
    void should_skip_when_daysOfCoverNull() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .daysOfCover(null)
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.stock.no_data");
      assertThat(result.explanation()).contains("No inventory data");
    }

    @Test
    @DisplayName("skips when currentPrice is null")
    void should_skip_when_currentPriceNull() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(null)
          .daysOfCover(new BigDecimal("30"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey())
          .isEqualTo("pricing.strategy.current_price_missing");
    }

    @Test
    @DisplayName("skips when currentPrice is zero")
    void should_skip_when_currentPriceZero() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(BigDecimal.ZERO)
          .daysOfCover(new BigDecimal("30"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey())
          .isEqualTo("pricing.strategy.current_price_missing");
    }
  }

  @Nested
  @DisplayName("edge cases")
  class EdgeCases {

    @Test
    @DisplayName("works with empty JSON object (all defaults)")
    void should_useDefaults_when_emptyParams() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .daysOfCover(new BigDecimal("3"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot("{}"));

      assertThat(result.rawTargetPrice()).isNotNull();
      assertThat(result.rawTargetPrice().compareTo(new BigDecimal("1000")))
          .isPositive();
    }

    @Test
    @DisplayName("produces correct explanation format for overstock")
    void should_produceExplanation_when_overstocked() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("5000"))
          .daysOfCover(new BigDecimal("85"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.explanation())
          .contains("days_of_cover=85.0")
          .contains("overstock_threshold=60")
          .contains("discount_factor=0.10")
          .contains("adjustment=")
          .contains("raw=");
    }

    @Test
    @DisplayName("includes frozen_capital in overstock explanation when available")
    void should_includeFrozenCapital_when_overstockedWithFrozenCapital() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("5000"))
          .daysOfCover(new BigDecimal("85"))
          .frozenCapital(new BigDecimal("450000"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.explanation())
          .contains("frozen_capital=450000")
          .contains("overstock_threshold=60");
    }

    @Test
    @DisplayName("omits frozen_capital when null")
    void should_omitFrozenCapital_when_null() {
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("5000"))
          .daysOfCover(new BigDecimal("85"))
          .frozenCapital(null)
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.explanation()).doesNotContain("frozen_capital");
    }

    @Test
    @DisplayName("daysOfCover just below critical triggers markup")
    void should_markup_when_justBelowCritical() {
      // daysOfCover=6.9 < critical=7 → near-stockout
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .daysOfCover(new BigDecimal("6.9"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("1050.00"));
    }

    @Test
    @DisplayName("daysOfCover just above overstock triggers discount")
    void should_discount_when_justAboveOverstock() {
      // daysOfCover=60.1 > overstock=60 → small discount
      // overshoot = (60.1 - 60) / 60 = 0.001667
      // discount = 0.001667 × 0.10 = 0.000167
      // raw ≈ 1000 × 0.999833 ≈ 999.83
      PricingSignalSet signals = signalsBuilder()
          .currentPrice(new BigDecimal("1000"))
          .daysOfCover(new BigDecimal("60.1"))
          .build();

      StrategyResult result = strategy.calculate(signals, policySnapshot(defaultParams()));

      assertThat(result.rawTargetPrice()).isNotNull();
      assertThat(result.rawTargetPrice().compareTo(new BigDecimal("1000")))
          .isNegative();
      assertThat(result.rawTargetPrice().compareTo(new BigDecimal("999")))
          .isPositive();
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
          .daysOfCover(new BigDecimal("30"))
          .build();

      PolicySnapshot policy = policySnapshot("not-a-json");

      assertThatThrownBy(() -> strategy.calculate(signals, policy))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Invalid STOCK_BALANCING strategy_params JSON");
    }
  }

  // --- helpers ---

  private PolicySnapshot policySnapshot(String strategyParams) {
    return new PolicySnapshot(
        1L, 1, "Stock Balancing Test", PolicyType.STOCK_BALANCING,
        strategyParams,
        null, null, null, null, null, ExecutionMode.RECOMMENDATION);
  }

  private String defaultParams() {
    return """
        {
          "criticalDaysOfCover": 7,
          "overstockDaysOfCover": 60,
          "stockoutMarkupPct": 0.05,
          "overstockDiscountFactor": 0.10,
          "maxDiscountPct": 0.20,
          "leadTimeDays": 14
        }
        """;
  }

  private static SignalSetBuilder signalsBuilder() {
    return new SignalSetBuilder();
  }

  static class SignalSetBuilder {

    private BigDecimal currentPrice;
    private BigDecimal daysOfCover;
    private BigDecimal frozenCapital;

    SignalSetBuilder currentPrice(BigDecimal v) {
      this.currentPrice = v;
      return this;
    }

    SignalSetBuilder daysOfCover(BigDecimal v) {
      this.daysOfCover = v;
      return this;
    }

    SignalSetBuilder frozenCapital(BigDecimal v) {
      this.frozenCapital = v;
      return this;
    }

    PricingSignalSet build() {
      return new PricingSignalSet(
          currentPrice, null, null, null, false, false,
          null, null, null, null, null, null, null, null,
          null, null, daysOfCover,
          frozenCapital, null,
          null, null, null);
    }
  }
}
