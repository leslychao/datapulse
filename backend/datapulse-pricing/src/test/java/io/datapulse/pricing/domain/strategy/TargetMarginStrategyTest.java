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

class TargetMarginStrategyTest {

  private TargetMarginStrategy strategy;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    strategy = new TargetMarginStrategy(objectMapper);
  }

  @Test
  @DisplayName("strategyType returns TARGET_MARGIN")
  void should_returnTargetMargin_when_callingStrategyType() {
    assertThat(strategy.strategyType()).isEqualTo(PolicyType.TARGET_MARGIN);
  }

  @Nested
  @DisplayName("calculate — happy path")
  class HappyPath {

    @Test
    @DisplayName("computes target price from COGS and manual commission")
    void should_computeTargetPrice_when_cogsAndManualCommissionProvided() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("500"))
          .currentPrice(new BigDecimal("1000"))
          .build();

      String params = """
          {
            "targetMarginPct": 0.20,
            "commissionSource": "MANUAL",
            "commissionManualPct": 0.15,
            "logisticsSource": "MANUAL",
            "logisticsManualAmount": 0
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isNotNull();
      assertThat(result.rawTargetPrice().compareTo(BigDecimal.ZERO)).isPositive();
      assertThat(result.explanation()).contains("target_margin");
      assertThat(result.reasonKey()).isNull();
    }

    @Test
    @DisplayName("correctly calculates price = COGS / (1 - margin - commission)")
    void should_calculateCorrectPrice_when_simpleCommission() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("600"))
          .currentPrice(new BigDecimal("1200"))
          .build();

      // price = 600 / (1 - 0.20 - 0.10) = 600 / 0.70 = 857.14
      String params = """
          {
            "targetMarginPct": 0.20,
            "commissionSource": "MANUAL",
            "commissionManualPct": 0.10,
            "logisticsSource": "MANUAL",
            "logisticsManualAmount": 0
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("857.14"));
    }

    @Test
    @DisplayName("includes logistics in effective cost rate when logistics provided")
    void should_includeLogistics_when_logisticsManualAmountProvided() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("400"))
          .currentPrice(new BigDecimal("1000"))
          .build();

      // logistics ratio = 100 / 1000 = 0.10
      // denominator = 1 - 0.20 - 0.10 - 0.10 = 0.60
      // price = 400 / 0.60 = 666.67
      String params = """
          {
            "targetMarginPct": 0.20,
            "commissionSource": "MANUAL",
            "commissionManualPct": 0.10,
            "logisticsSource": "MANUAL",
            "logisticsManualAmount": 100
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("666.67"));
    }
  }

  @Nested
  @DisplayName("calculate — skip cases")
  class SkipCases {

    @Test
    @DisplayName("skips when COGS is null")
    void should_skip_when_cogsIsNull() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(null)
          .currentPrice(new BigDecimal("1000"))
          .build();

      PolicySnapshot policy = policySnapshot(defaultParams());

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.strategy.cogs_missing");
    }

    @Test
    @DisplayName("skips when COGS is zero")
    void should_skip_when_cogsIsZero() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(BigDecimal.ZERO)
          .currentPrice(new BigDecimal("1000"))
          .build();

      PolicySnapshot policy = policySnapshot(defaultParams());

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.strategy.cogs_missing");
    }

    @Test
    @DisplayName("skips when COGS is negative")
    void should_skip_when_cogsIsNegative() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("-50"))
          .currentPrice(new BigDecimal("1000"))
          .build();

      PolicySnapshot policy = policySnapshot(defaultParams());

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.strategy.cogs_missing");
    }

    @Test
    @DisplayName("skips when commission is not available (AUTO with null signal)")
    void should_skip_when_commissionNotAvailable() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("500"))
          .avgCommissionPct(null)
          .currentPrice(new BigDecimal("1000"))
          .build();

      String params = """
          {
            "targetMarginPct": 0.20,
            "commissionSource": "AUTO",
            "logisticsSource": "MANUAL",
            "logisticsManualAmount": 0
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.strategy.commission_missing");
    }

    @Test
    @DisplayName("skips when denominator <= 0 (margin + costs >= 100%)")
    void should_skip_when_denominatorInvalid() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("500"))
          .currentPrice(new BigDecimal("1000"))
          .build();

      // denominator = 1 - 0.60 - 0.50 = -0.10 → skip
      String params = """
          {
            "targetMarginPct": 0.60,
            "commissionSource": "MANUAL",
            "commissionManualPct": 0.50,
            "logisticsSource": "MANUAL",
            "logisticsManualAmount": 0
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.strategy.denominator_invalid");
    }
  }

  @Nested
  @DisplayName("commission resolution")
  class CommissionResolution {

    @Test
    @DisplayName("AUTO_WITH_MANUAL_FALLBACK uses auto when available")
    void should_useAutoCommission_when_autoAvailable() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("500"))
          .currentPrice(new BigDecimal("1000"))
          .avgCommissionPct(new BigDecimal("0.12"))
          .build();

      String params = """
          {
            "targetMarginPct": 0.20,
            "commissionSource": "AUTO_WITH_MANUAL_FALLBACK",
            "commissionManualPct": 0.15,
            "logisticsSource": "MANUAL",
            "logisticsManualAmount": 0
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      // denominator = 1 - 0.20 - 0.12 = 0.68
      // price = 500 / 0.68 = 735.29
      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("735.29"));
    }

    @Test
    @DisplayName("AUTO_WITH_MANUAL_FALLBACK falls back to manual when auto is null")
    void should_fallbackToManual_when_autoCommissionNull() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("500"))
          .currentPrice(new BigDecimal("1000"))
          .avgCommissionPct(null)
          .build();

      String params = """
          {
            "targetMarginPct": 0.20,
            "commissionSource": "AUTO_WITH_MANUAL_FALLBACK",
            "commissionManualPct": 0.15,
            "logisticsSource": "MANUAL",
            "logisticsManualAmount": 0
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      // denominator = 1 - 0.20 - 0.15 = 0.65
      // price = 500 / 0.65 = 769.23
      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("769.23"));
    }
  }

  @Nested
  @DisplayName("invalid params")
  class InvalidParams {

    @Test
    @DisplayName("throws IllegalStateException on invalid JSON params")
    void should_throwIllegalState_when_invalidJson() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("500"))
          .currentPrice(new BigDecimal("1000"))
          .build();

      PolicySnapshot policy = policySnapshot("not-a-json");

      assertThatThrownBy(() -> strategy.calculate(signals, policy))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Invalid TARGET_MARGIN strategy_params JSON");
    }
  }

  @Nested
  @DisplayName("logistics resolution")
  class LogisticsResolution {

    @Test
    @DisplayName("AUTO_WITH_MANUAL_FALLBACK uses auto logistics when available")
    void should_useAutoLogistics_when_autoAvailable() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("500"))
          .currentPrice(new BigDecimal("1000"))
          .avgLogisticsPerUnit(new BigDecimal("50"))
          .build();

      String params = """
          {
            "targetMarginPct": 0.20,
            "commissionSource": "MANUAL",
            "commissionManualPct": 0.10,
            "logisticsSource": "AUTO_WITH_MANUAL_FALLBACK",
            "logisticsManualAmount": 100
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      // logistics ratio = 50 / 1000 = 0.05
      // denominator = 1 - 0.20 - 0.10 - 0.05 = 0.65
      // price = 500 / 0.65 = 769.23
      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("769.23"));
    }

    @Test
    @DisplayName("AUTO_WITH_MANUAL_FALLBACK falls back to manual when auto is null")
    void should_fallbackToManualLogistics_when_autoNull() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("500"))
          .currentPrice(new BigDecimal("1000"))
          .avgLogisticsPerUnit(null)
          .build();

      String params = """
          {
            "targetMarginPct": 0.20,
            "commissionSource": "MANUAL",
            "commissionManualPct": 0.10,
            "logisticsSource": "AUTO_WITH_MANUAL_FALLBACK",
            "logisticsManualAmount": 100
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      // logistics ratio = 100 / 1000 = 0.10
      // denominator = 1 - 0.20 - 0.10 - 0.10 = 0.60
      // price = 500 / 0.60 = 833.33
      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("833.33"));
    }
  }

  @Nested
  @DisplayName("return adjustment and ad cost")
  class ReturnAndAdCost {

    @Test
    @DisplayName("includes return rate in cost calculation when enabled")
    void should_includeReturnRate_when_flagEnabled() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("500"))
          .currentPrice(new BigDecimal("1000"))
          .avgCommissionPct(new BigDecimal("0.10"))
          .build();

      String params = """
          {
            "targetMarginPct": 0.20,
            "commissionSource": "AUTO",
            "logisticsSource": "MANUAL",
            "logisticsManualAmount": 0,
            "includeReturnAdjustment": true
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      // Return rate signal is null → treated as 0
      // result should be same as without return adjustment
      assertThat(result.rawTargetPrice()).isNotNull();
    }

    @Test
    @DisplayName("includes ad cost in cost calculation when enabled with signal")
    void should_includeAdCost_when_flagEnabledAndSignalPresent() {
      PricingSignalSet signals = new PricingSignalSet(
          new BigDecimal("1000"), new BigDecimal("500"), null, null,
          false, false,
          new BigDecimal("0.10"), null, null, new BigDecimal("0.05"),
          null, null, null);

      String params = """
          {
            "targetMarginPct": 0.20,
            "commissionSource": "AUTO",
            "logisticsSource": "MANUAL",
            "logisticsManualAmount": 0,
            "includeAdCost": true
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      // denominator = 1 - 0.20 - 0.10 - 0.05 = 0.65
      // price = 500 / 0.65 = 769.23
      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("769.23"));
    }
  }

  @Nested
  @DisplayName("denominator edge cases")
  class DenominatorEdgeCases {

    @Test
    @DisplayName("skips when denominator is exactly zero")
    void should_skip_when_denominatorExactlyZero() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("500"))
          .currentPrice(new BigDecimal("1000"))
          .build();

      // 1 - 0.50 - 0.50 = 0 → skip
      String params = """
          {
            "targetMarginPct": 0.50,
            "commissionSource": "MANUAL",
            "commissionManualPct": 0.50,
            "logisticsSource": "MANUAL",
            "logisticsManualAmount": 0
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey()).isEqualTo("pricing.strategy.denominator_invalid");
    }
  }

  @Nested
  @DisplayName("rounding")
  class Rounding {

    @Test
    @DisplayName("rounds result to 2 decimal places")
    void should_roundTo2Decimals_when_resultHasMorePrecision() {
      PricingSignalSet signals = signalsBuilder()
          .cogs(new BigDecimal("333"))
          .currentPrice(new BigDecimal("1000"))
          .build();

      // price = 333 / (1 - 0.20 - 0.15) = 333 / 0.65 = 512.307692...
      String params = """
          {
            "targetMarginPct": 0.20,
            "commissionSource": "MANUAL",
            "commissionManualPct": 0.15,
            "logisticsSource": "MANUAL",
            "logisticsManualAmount": 0
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice().scale()).isLessThanOrEqualTo(2);
    }
  }

  // --- helpers ---

  private PolicySnapshot policySnapshot(String strategyParams) {
    return new PolicySnapshot(
        1L, 1, "Test Policy", PolicyType.TARGET_MARGIN, strategyParams,
        null, null, null, null, null, ExecutionMode.RECOMMENDATION);
  }

  private String defaultParams() {
    return """
        {
          "targetMarginPct": 0.20,
          "commissionSource": "MANUAL",
          "commissionManualPct": 0.15,
          "logisticsSource": "MANUAL",
          "logisticsManualAmount": 0
        }
        """;
  }

  private static SignalSetBuilder signalsBuilder() {
    return new SignalSetBuilder();
  }

  static class SignalSetBuilder {

    private BigDecimal currentPrice;
    private BigDecimal cogs;
    private BigDecimal avgCommissionPct;
    private BigDecimal avgLogisticsPerUnit;

    SignalSetBuilder currentPrice(BigDecimal v) { this.currentPrice = v; return this; }
    SignalSetBuilder cogs(BigDecimal v) { this.cogs = v; return this; }
    SignalSetBuilder avgCommissionPct(BigDecimal v) { this.avgCommissionPct = v; return this; }
    SignalSetBuilder avgLogisticsPerUnit(BigDecimal v) { this.avgLogisticsPerUnit = v; return this; }

    PricingSignalSet build() {
      return new PricingSignalSet(
          currentPrice, cogs, null, null, false, false,
          avgCommissionPct, avgLogisticsPerUnit,
          null, null, null, null, null);
    }
  }
}
