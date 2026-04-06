package io.datapulse.pricing.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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

class CompetitorAnchorStrategyTest {

  private CompetitorAnchorStrategy strategy;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    strategy = new CompetitorAnchorStrategy(objectMapper);
  }

  @Test
  @DisplayName("strategyType returns COMPETITOR_ANCHOR")
  void should_returnCompetitorAnchor_when_callingStrategyType() {
    assertThat(strategy.strategyType()).isEqualTo(PolicyType.COMPETITOR_ANCHOR);
  }

  @Nested
  @DisplayName("anchor price calculation")
  class AnchorPrice {

    @Test
    @DisplayName("returns competitor price as target when positionFactor is 1.0")
    void should_returnCompetitorPrice_when_positionFactorIsOne() {
      PricingSignalSet signals = signalsBuilder()
          .competitorPrice(new BigDecimal("3500"))
          .build();

      String params = """
          { "positionFactor": 1.0, "useMarginFloor": false }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("3500"));
      assertThat(result.reasonKey()).isNull();
    }

    @Test
    @DisplayName("applies positionFactor to anchor (0.95 = 5% cheaper)")
    void should_applyPositionFactor_when_lessThanOne() {
      PricingSignalSet signals = signalsBuilder()
          .competitorPrice(new BigDecimal("3500"))
          .build();

      String params = """
          { "positionFactor": 0.95, "useMarginFloor": false }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("3325.00"));
    }

    @Test
    @DisplayName("applies positionFactor > 1 for premium positioning")
    void should_applyPositionFactor_when_greaterThanOne() {
      PricingSignalSet signals = signalsBuilder()
          .competitorPrice(new BigDecimal("1000"))
          .build();

      String params = """
          { "positionFactor": 1.10, "useMarginFloor": false }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("1100.00"));
    }
  }

  @Nested
  @DisplayName("margin floor")
  class MarginFloor {

    @Test
    @DisplayName("uses margin floor when anchor price is too low")
    void should_useMarginFloor_when_anchorBelowFloor() {
      PricingSignalSet signals = signalsBuilder()
          .competitorPrice(new BigDecimal("500"))
          .cogs(new BigDecimal("400"))
          .currentPrice(new BigDecimal("600"))
          .build();

      // anchor = 500 × 0.95 = 475
      // margin_floor = 400 / (1 - 0.10 - 0) = 400 / 0.90 = 444.44
      // target = max(475, 444.44) = 475
      String params = """
          {
            "positionFactor": 0.95,
            "minMarginPct": 0.10,
            "useMarginFloor": true
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("475.00"));
    }

    @Test
    @DisplayName("margin floor lifts price above anchor when COGS is high")
    void should_liftPrice_when_cogsRequiresHigherMargin() {
      PricingSignalSet signals = signalsBuilder()
          .competitorPrice(new BigDecimal("500"))
          .cogs(new BigDecimal("450"))
          .currentPrice(new BigDecimal("600"))
          .build();

      // anchor = 500 × 0.90 = 450
      // margin_floor = 450 / (1 - 0.10 - 0) = 450 / 0.90 = 500
      // target = max(450, 500) = 500
      String params = """
          {
            "positionFactor": 0.90,
            "minMarginPct": 0.10,
            "useMarginFloor": true
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("uses fallback 2x COGS when denominator <= 0")
    void should_useFallbackMarginFloor_when_denominatorInvalid() {
      PricingSignalSet signals = signalsBuilder()
          .competitorPrice(new BigDecimal("300"))
          .cogs(new BigDecimal("200"))
          .currentPrice(new BigDecimal("400"))
          .avgCommissionPct(new BigDecimal("0.50"))
          .build();

      // effective_cost_rate = 0.50
      // denominator = 1 - 0.10 - 0.50 = 0.40 > 0, floor = 200/0.40 = 500
      // anchor = 300 × 1.0 = 300
      // target = max(300, 500) = 500
      String params = """
          {
            "positionFactor": 1.0,
            "minMarginPct": 0.10,
            "useMarginFloor": true
          }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("skips margin floor when useMarginFloor is false")
    void should_skipMarginFloor_when_disabled() {
      PricingSignalSet signals = signalsBuilder()
          .competitorPrice(new BigDecimal("300"))
          .cogs(new BigDecimal("400"))
          .currentPrice(new BigDecimal("500"))
          .build();

      String params = """
          { "positionFactor": 1.0, "useMarginFloor": false }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("skips margin floor when COGS is null")
    void should_skipMarginFloor_when_cogsNull() {
      PricingSignalSet signals = signalsBuilder()
          .competitorPrice(new BigDecimal("500"))
          .cogs(null)
          .build();

      String params = """
          { "positionFactor": 1.0, "useMarginFloor": true }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice())
          .isEqualByComparingTo(new BigDecimal("500.00"));
    }
  }

  @Nested
  @DisplayName("skip cases")
  class SkipCases {

    @Test
    @DisplayName("skips when competitor price is null")
    void should_skip_when_noCompetitorData() {
      PricingSignalSet signals = signalsBuilder()
          .competitorPrice(null)
          .build();

      PolicySnapshot policy = policySnapshot(defaultParams());

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.reasonKey())
          .isEqualTo("pricing.competitor.missing");
    }
  }

  @Nested
  @DisplayName("explanation")
  class Explanation {

    @Test
    @DisplayName("explanation contains competitor_price and position_factor")
    void should_includeDetails_when_calculated() {
      PricingSignalSet signals = signalsBuilder()
          .competitorPrice(new BigDecimal("3500"))
          .build();

      String params = """
          { "positionFactor": 0.95, "useMarginFloor": false }
          """;
      PolicySnapshot policy = policySnapshot(params);

      StrategyResult result = strategy.calculate(signals, policy);

      assertThat(result.explanation())
          .contains("competitor_price=3500")
          .contains("position_factor=0.95");
    }
  }

  @Nested
  @DisplayName("invalid params")
  class InvalidParams {

    @Test
    @DisplayName("throws on invalid JSON")
    void should_throwIllegalState_when_invalidJson() {
      PricingSignalSet signals = signalsBuilder()
          .competitorPrice(new BigDecimal("1000"))
          .build();

      PolicySnapshot policy = policySnapshot("not-json");

      assertThatThrownBy(() -> strategy.calculate(signals, policy))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("COMPETITOR_ANCHOR");
    }
  }

  // --- helpers ---

  private PolicySnapshot policySnapshot(String strategyParams) {
    return new PolicySnapshot(
        1L, 1, "Test Policy", PolicyType.COMPETITOR_ANCHOR, strategyParams,
        null, null, null, null, null, ExecutionMode.RECOMMENDATION);
  }

  private String defaultParams() {
    return """
        { "positionFactor": 1.0, "useMarginFloor": false }
        """;
  }

  private static SignalSetBuilder signalsBuilder() {
    return new SignalSetBuilder();
  }

  static class SignalSetBuilder {

    private BigDecimal currentPrice;
    private BigDecimal cogs;
    private BigDecimal competitorPrice;
    private BigDecimal avgCommissionPct;

    SignalSetBuilder currentPrice(BigDecimal v) { this.currentPrice = v; return this; }
    SignalSetBuilder cogs(BigDecimal v) { this.cogs = v; return this; }
    SignalSetBuilder competitorPrice(BigDecimal v) { this.competitorPrice = v; return this; }
    SignalSetBuilder avgCommissionPct(BigDecimal v) { this.avgCommissionPct = v; return this; }

    PricingSignalSet build() {
      return new PricingSignalSet(
          currentPrice, cogs, null, null, false, false,
          avgCommissionPct, null, null, null, null, null, null, null,
          null, null, null, null, null,
          competitorPrice, null, null);
    }
  }
}
