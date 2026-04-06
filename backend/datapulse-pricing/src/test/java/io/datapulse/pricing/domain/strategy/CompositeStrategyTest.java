package io.datapulse.pricing.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.pricing.domain.ExecutionMode;
import io.datapulse.pricing.domain.PolicySnapshot;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.domain.PricingSignalSet;
import io.datapulse.pricing.domain.StrategyResult;

@ExtendWith(MockitoExtension.class)
class CompositeStrategyTest {

  private CompositeStrategy strategy;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock
  private PricingStrategyRegistry registry;

  @Mock
  private PricingStrategy targetMarginStrategy;

  @Mock
  private PricingStrategy velocityAdaptiveStrategy;

  @Mock
  private PricingStrategy stockBalancingStrategy;

  @BeforeEach
  void setUp() {
    strategy = new CompositeStrategy(registry, objectMapper);
    lenient().when(registry.resolve(PolicyType.TARGET_MARGIN))
        .thenReturn(targetMarginStrategy);
    lenient().when(registry.resolve(PolicyType.VELOCITY_ADAPTIVE))
        .thenReturn(velocityAdaptiveStrategy);
    lenient().when(registry.resolve(PolicyType.STOCK_BALANCING))
        .thenReturn(stockBalancingStrategy);
  }

  @Test
  @DisplayName("strategyType returns COMPOSITE")
  void should_returnComposite_when_callingStrategyType() {
    assertThat(strategy.strategyType()).isEqualTo(PolicyType.COMPOSITE);
  }

  @Nested
  @DisplayName("two components, both success — weighted average")
  class TwoComponentsBothSuccess {

    @Test
    @DisplayName("calculates weighted average of two successful components")
    void should_calculateWeightedAverage_when_bothComponentsSucceed() {
      // TARGET_MARGIN w=0.60 → 3890
      // VELOCITY_ADAPTIVE w=0.40 → 3750
      // weighted = 3890 * 0.60 + 3750 * 0.40 = 2334 + 1500 = 3834
      when(targetMarginStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(
              new BigDecimal("3890"), "target_margin=25%"));
      when(velocityAdaptiveStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(
              new BigDecimal("3750"), "ratio=0.66, adj=-4.3%"));

      String params = """
          {
            "components": [
              {
                "strategyType": "TARGET_MARGIN",
                "weight": 0.60,
                "strategyParams": "{\\"targetMarginPct\\": 0.25}"
              },
              {
                "strategyType": "VELOCITY_ADAPTIVE",
                "weight": 0.40,
                "strategyParams": "{}"
              }
            ]
          }
          """;

      StrategyResult result = strategy.calculate(defaultSignals(), policySnapshot(params));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("3834.00"));
      assertThat(result.reasonKey()).isNull();
      assertThat(result.explanation()).contains("COMPOSITE (2/2 components)");
      assertThat(result.explanation()).contains("TARGET_MARGIN");
      assertThat(result.explanation()).contains("VELOCITY_ADAPTIVE");
      assertThat(result.explanation()).contains("weighted_raw=3834.00");
    }

    @Test
    @DisplayName("calculates weighted average with unequal weights")
    void should_handleUnequalWeights_when_weightsNotSumToOne() {
      // TARGET_MARGIN w=3 → 1000
      // VELOCITY_ADAPTIVE w=7 → 2000
      // totalWeight = 10
      // weighted = 1000 * (3/10) + 2000 * (7/10) = 300 + 1400 = 1700
      when(targetMarginStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(
              new BigDecimal("1000"), "margin calc"));
      when(velocityAdaptiveStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(
              new BigDecimal("2000"), "velocity calc"));

      String params = """
          {
            "components": [
              {
                "strategyType": "TARGET_MARGIN",
                "weight": 3,
                "strategyParams": "{\\"targetMarginPct\\": 0.25}"
              },
              {
                "strategyType": "VELOCITY_ADAPTIVE",
                "weight": 7,
                "strategyParams": "{}"
              }
            ]
          }
          """;

      StrategyResult result = strategy.calculate(defaultSignals(), policySnapshot(params));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("1700.00"));
    }
  }

  @Nested
  @DisplayName("three components, one SKIP — weight renormalization")
  class ThreeComponentsOneSkip {

    @Test
    @DisplayName("renormalizes weights when one component skips")
    void should_renormalizeWeights_when_oneComponentSkips() {
      // TARGET_MARGIN w=0.50 → 4000
      // VELOCITY_ADAPTIVE w=0.30 → SKIP
      // STOCK_BALANCING w=0.20 → 3000
      // effective weights: TM = 0.50/(0.50+0.20) = 0.7143, SB = 0.20/0.70 = 0.2857
      // weighted = 4000 * 0.7143 + 3000 * 0.2857 = 2857.14 + 857.14 = 3714.29
      when(targetMarginStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(
              new BigDecimal("4000"), "margin ok"));
      when(velocityAdaptiveStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.skip(
              "Insufficient sales data", "pricing.velocity.insufficient_data"));
      when(stockBalancingStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(
              new BigDecimal("3000"), "stock ok"));

      String params = """
          {
            "components": [
              {
                "strategyType": "TARGET_MARGIN",
                "weight": 0.50,
                "strategyParams": "{\\"targetMarginPct\\": 0.25}"
              },
              {
                "strategyType": "VELOCITY_ADAPTIVE",
                "weight": 0.30,
                "strategyParams": "{}"
              },
              {
                "strategyType": "STOCK_BALANCING",
                "weight": 0.20,
                "strategyParams": "{}"
              }
            ]
          }
          """;

      StrategyResult result = strategy.calculate(defaultSignals(), policySnapshot(params));

      assertThat(result.rawTargetPrice()).isNotNull();
      assertThat(result.reasonKey()).isNull();
      // 4000 * (0.50/0.70) + 3000 * (0.20/0.70) ≈ 3714.29
      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("3714.29"));
      assertThat(result.explanation()).contains("COMPOSITE (2/3 components)");
      assertThat(result.explanation()).contains("VELOCITY_ADAPTIVE SKIPPED");
    }
  }

  @Nested
  @DisplayName("all components SKIP")
  class AllComponentsSkip {

    @Test
    @DisplayName("returns skip when all components skip")
    void should_returnSkip_when_allComponentsSkip() {
      when(targetMarginStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.skip("COGS not available",
              "pricing.strategy.cogs_missing"));
      when(velocityAdaptiveStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.skip("Insufficient data",
              "pricing.velocity.insufficient_data"));

      String params = """
          {
            "components": [
              {
                "strategyType": "TARGET_MARGIN",
                "weight": 0.60,
                "strategyParams": "{\\"targetMarginPct\\": 0.25}"
              },
              {
                "strategyType": "VELOCITY_ADAPTIVE",
                "weight": 0.40,
                "strategyParams": "{}"
              }
            ]
          }
          """;

      StrategyResult result = strategy.calculate(defaultSignals(), policySnapshot(params));

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey())
          .isEqualTo("pricing.composite.all_skipped");
      assertThat(result.explanation()).contains("COMPOSITE (0/2 components)");
      assertThat(result.explanation()).contains("TARGET_MARGIN SKIPPED");
      assertThat(result.explanation()).contains("VELOCITY_ADAPTIVE SKIPPED");
      assertThat(result.explanation()).contains("All component strategies skipped");
    }
  }

  @Nested
  @DisplayName("single component — degenerate case")
  class SingleComponent {

    @Test
    @DisplayName("passes through single component price with weight=1.0")
    void should_passThroughPrice_when_singleComponent() {
      when(targetMarginStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(
              new BigDecimal("5000"), "single strategy"));

      String params = """
          {
            "components": [
              {
                "strategyType": "TARGET_MARGIN",
                "weight": 1.0,
                "strategyParams": "{\\"targetMarginPct\\": 0.25}"
              }
            ]
          }
          """;

      StrategyResult result = strategy.calculate(defaultSignals(), policySnapshot(params));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("5000.00"));
      assertThat(result.explanation()).contains("COMPOSITE (1/1 components)");
    }

    @Test
    @DisplayName("single component with arbitrary weight still yields same price")
    void should_yieldSamePrice_when_singleComponentAnyWeight() {
      when(targetMarginStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(
              new BigDecimal("4200"), "single"));

      String params = """
          {
            "components": [
              {
                "strategyType": "TARGET_MARGIN",
                "weight": 42.0,
                "strategyParams": "{\\"targetMarginPct\\": 0.25}"
              }
            ]
          }
          """;

      StrategyResult result = strategy.calculate(defaultSignals(), policySnapshot(params));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("4200.00"));
    }
  }

  @Nested
  @DisplayName("explanation format")
  class ExplanationFormat {

    @Test
    @DisplayName("includes per-component breakdown with weights and prices")
    void should_includePerComponentBreakdown_when_mixedResults() {
      when(targetMarginStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(
              new BigDecimal("3890"), "target_margin=25%, raw=3890"));
      when(velocityAdaptiveStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(
              new BigDecimal("3750"), "ratio=0.66, adj=-4.3%"));
      when(stockBalancingStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.skip(
              "no inventory data", "pricing.stock.no_data"));

      String params = """
          {
            "components": [
              {
                "strategyType": "TARGET_MARGIN",
                "weight": 0.50,
                "strategyParams": "{\\"targetMarginPct\\": 0.25}"
              },
              {
                "strategyType": "VELOCITY_ADAPTIVE",
                "weight": 0.30,
                "strategyParams": "{}"
              },
              {
                "strategyType": "STOCK_BALANCING",
                "weight": 0.20,
                "strategyParams": "{}"
              }
            ]
          }
          """;

      StrategyResult result = strategy.calculate(defaultSignals(), policySnapshot(params));

      assertThat(result.explanation())
          .contains("COMPOSITE (2/3 components)")
          .contains("TARGET_MARGIN w=0.50")
          .contains("3890")
          .contains("VELOCITY_ADAPTIVE w=0.30")
          .contains("3750")
          .contains("STOCK_BALANCING SKIPPED")
          .contains("weighted_raw=");
    }
  }

  @Nested
  @DisplayName("invalid params")
  class InvalidParams {

    @Test
    @DisplayName("throws IllegalStateException on invalid JSON params")
    void should_throwIllegalState_when_invalidJson() {
      PolicySnapshot policy = policySnapshot("not-a-json");

      assertThatThrownBy(() -> strategy.calculate(defaultSignals(), policy))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Invalid COMPOSITE strategy_params JSON");
    }
  }

  @Nested
  @DisplayName("equal weights")
  class EqualWeights {

    @Test
    @DisplayName("calculates simple average when weights are equal")
    void should_calculateSimpleAverage_when_equalWeights() {
      // Both weight=1.0
      // TARGET_MARGIN → 4000, VELOCITY_ADAPTIVE → 3000
      // average = (4000 + 3000) / 2 = 3500
      when(targetMarginStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(
              new BigDecimal("4000"), "margin"));
      when(velocityAdaptiveStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(
              new BigDecimal("3000"), "velocity"));

      String params = """
          {
            "components": [
              {
                "strategyType": "TARGET_MARGIN",
                "weight": 1.0,
                "strategyParams": "{\\"targetMarginPct\\": 0.25}"
              },
              {
                "strategyType": "VELOCITY_ADAPTIVE",
                "weight": 1.0,
                "strategyParams": "{}"
              }
            ]
          }
          """;

      StrategyResult result = strategy.calculate(defaultSignals(), policySnapshot(params));

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("3500.00"));
    }
  }

  // --- helpers ---

  private PolicySnapshot policySnapshot(String strategyParams) {
    return new PolicySnapshot(
        1L, 1, "Composite Test", PolicyType.COMPOSITE, strategyParams,
        null, null, null, null, null, ExecutionMode.RECOMMENDATION);
  }

  private PricingSignalSet defaultSignals() {
    return new PricingSignalSet(
        new BigDecimal("5000"), new BigDecimal("2000"),
        "ACTIVE", 100, false, false,
        new BigDecimal("0.15"), new BigDecimal("300"),
        new BigDecimal("0.05"), new BigDecimal("0.10"),
        null, null, null, null,
        new BigDecimal("3.5"), new BigDecimal("3.0"),
        new BigDecimal("45"), null, null,
        null, null, null);
  }
}
