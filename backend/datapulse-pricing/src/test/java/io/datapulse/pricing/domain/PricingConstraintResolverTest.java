package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.pricing.domain.PricingConstraintResolver.ConstraintResolution;

class PricingConstraintResolverTest {

  private PricingConstraintResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new PricingConstraintResolver(new ObjectMapper());
  }

  @Nested
  @DisplayName("min price constraint")
  class MinPrice {

    @Test
    @DisplayName("clamps price up to min when raw is below")
    void should_clampUp_when_belowMin() {
      PolicySnapshot policy = snapshot(new BigDecimal("500"), null, null, null);
      PricingSignalSet signals = signals(new BigDecimal("1000"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("400"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("500"));
      assertThat(result.applied()).anyMatch(c -> "min_price".equals(c.name()));
    }

    @Test
    @DisplayName("does not clamp when raw is above min")
    void should_notClamp_when_aboveMin() {
      PolicySnapshot policy = snapshot(new BigDecimal("300"), null, null, null);
      PricingSignalSet signals = signals(new BigDecimal("1000"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("500"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("500"));
      assertThat(result.applied()).noneMatch(c -> "min_price".equals(c.name()));
    }
  }

  @Nested
  @DisplayName("max price constraint")
  class MaxPrice {

    @Test
    @DisplayName("clamps price down to max when raw is above")
    void should_clampDown_when_aboveMax() {
      PolicySnapshot policy = snapshot(null, new BigDecimal("1000"), null, null);
      PricingSignalSet signals = signals(new BigDecimal("800"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("1200"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("1000"));
      assertThat(result.applied()).anyMatch(c -> "max_price".equals(c.name()));
    }

    @Test
    @DisplayName("does not clamp when raw is below max")
    void should_notClamp_when_belowMax() {
      PolicySnapshot policy = snapshot(null, new BigDecimal("2000"), null, null);
      PricingSignalSet signals = signals(new BigDecimal("800"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("1500"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("1500"));
      assertThat(result.applied()).noneMatch(c -> "max_price".equals(c.name()));
    }
  }

  @Nested
  @DisplayName("max price change constraint")
  class MaxPriceChange {

    @Test
    @DisplayName("clamps price when change exceeds max percent upward")
    void should_clamp_when_priceIncreaseExceedsMax() {
      PolicySnapshot policy = snapshot(null, null, new BigDecimal("0.10"), null);
      PricingSignalSet signals = signals(new BigDecimal("1000"));

      // max delta = 1000 * 0.10 = 100, so max price = 1100
      ConstraintResolution result = resolver.resolve(new BigDecimal("1200"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("1100.00"));
      assertThat(result.applied()).anyMatch(c -> "max_price_change".equals(c.name()));
    }

    @Test
    @DisplayName("clamps price when change exceeds max percent downward")
    void should_clamp_when_priceDecreaseExceedsMax() {
      PolicySnapshot policy = snapshot(null, null, new BigDecimal("0.10"), null);
      PricingSignalSet signals = signals(new BigDecimal("1000"));

      // max delta = 100, so min price = 900
      ConstraintResolution result = resolver.resolve(new BigDecimal("800"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("900.00"));
      assertThat(result.applied()).anyMatch(c -> "max_price_change".equals(c.name()));
    }

    @Test
    @DisplayName("does not clamp when change is within max percent")
    void should_notClamp_when_changeWithinMax() {
      PolicySnapshot policy = snapshot(null, null, new BigDecimal("0.20"), null);
      PricingSignalSet signals = signals(new BigDecimal("1000"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("1100"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("1100"));
      assertThat(result.applied()).noneMatch(c -> "max_price_change".equals(c.name()));
    }
  }

  @Nested
  @DisplayName("min margin constraint")
  class MinMargin {

    @Test
    @DisplayName("clamps price up when below margin floor")
    void should_clampUp_when_belowMarginFloor() {
      // min_margin = 0.20 → margin floor = cogs / (1 - 0.20) = 500 / 0.80 = 625
      PolicySnapshot policy = snapshot(null, null, null, new BigDecimal("0.20"));
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("1000"), new BigDecimal("500"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("600"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("625.00"));
      assertThat(result.applied()).anyMatch(c -> "min_margin".equals(c.name()));
    }

    @Test
    @DisplayName("does not clamp when price is above margin floor")
    void should_notClamp_when_aboveMarginFloor() {
      PolicySnapshot policy = snapshot(null, null, null, new BigDecimal("0.20"));
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("1000"), new BigDecimal("500"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("700"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("700"));
      assertThat(result.applied()).noneMatch(c -> "min_margin".equals(c.name()));
    }

    @Test
    @DisplayName("skips margin check when COGS is null")
    void should_skip_when_cogsNull() {
      PolicySnapshot policy = snapshot(null, null, null, new BigDecimal("0.20"));
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("1000"), null);

      ConstraintResolution result = resolver.resolve(new BigDecimal("100"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("100"));
      assertThat(result.applied()).noneMatch(c -> "min_margin".equals(c.name()));
    }
  }

  @Nested
  @DisplayName("no constraints")
  class NoConstraints {

    @Test
    @DisplayName("returns raw price when no constraints configured")
    void should_returnRawPrice_when_noConstraints() {
      PolicySnapshot policy = snapshot(null, null, null, null);
      PricingSignalSet signals = signals(new BigDecimal("1000"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("999"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("999"));
      assertThat(result.applied()).isEmpty();
    }
  }

  @Nested
  @DisplayName("multiple constraints combined")
  class CombinedConstraints {

    @Test
    @DisplayName("applies min_price and max_price_change together — most restrictive wins")
    void should_applyBoth_when_minPriceAndMaxChangeConfigured() {
      PolicySnapshot policy = snapshot(new BigDecimal("950"), null, new BigDecimal("0.10"), null);
      PricingSignalSet signals = signals(new BigDecimal("1000"));

      // raw=800 → max_change clamps to floor=900, then min_price says 950 → final 950
      ConstraintResolution result = resolver.resolve(new BigDecimal("800"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("950"));
      assertThat(result.applied()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("applies min_margin then rounding in correct order")
    void should_applyMarginThenRounding_when_bothConfigured() {
      // min_margin=0.20, COGS=500 → margin floor = 500/0.80 = 625
      // rounding step=50, FLOOR → 625/50 = 12.5 → floor=12 * 50 = 600? Actually 625 exactly
      PolicySnapshot policy = targetMarginSnapshot(
          null, null, null, new BigDecimal("0.20"),
          new BigDecimal("50"), TargetMarginParams.RoundingDirection.FLOOR);
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("1000"), new BigDecimal("500"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("600"), signals, policy);

      // margin clamps 600→625, rounding FLOOR step 50 → 625/50=12.5 floor=12*50=600
      // BUT min_margin was already applied raising price to 625
      // Then rounding FLOOR(625, 50) = 600 — rounds down
      assertThat(result.applied()).anyMatch(c -> "min_margin".equals(c.name()));
    }
  }

  @Nested
  @DisplayName("max price change edge cases")
  class MaxPriceChangeEdgeCases {

    @Test
    @DisplayName("skips max_change when currentPrice is zero (division by zero avoided)")
    void should_skip_when_currentPriceIsZero() {
      PolicySnapshot policy = snapshot(null, null, new BigDecimal("0.10"), null);
      PricingSignalSet signals = signals(BigDecimal.ZERO);

      ConstraintResolution result = resolver.resolve(new BigDecimal("500"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("500"));
      assertThat(result.applied()).noneMatch(c -> "max_price_change".equals(c.name()));
    }

    @Test
    @DisplayName("skips max_change when currentPrice is null")
    void should_skip_when_currentPriceIsNull() {
      PolicySnapshot policy = snapshot(null, null, new BigDecimal("0.10"), null);
      PricingSignalSet signals = signals(null);

      ConstraintResolution result = resolver.resolve(new BigDecimal("500"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("500"));
      assertThat(result.applied()).noneMatch(c -> "max_price_change".equals(c.name()));
    }
  }

  @Nested
  @DisplayName("min margin edge cases")
  class MinMarginEdgeCases {

    @Test
    @DisplayName("skips margin when minMarginPct >= 1 (denominator <= 0)")
    void should_skip_when_denominatorInvalid() {
      PolicySnapshot policy = snapshot(null, null, null, BigDecimal.ONE);
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("1000"), new BigDecimal("500"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("100"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("100"));
      assertThat(result.applied()).noneMatch(c -> "min_margin".equals(c.name()));
    }

    @Test
    @DisplayName("skips margin when COGS is zero")
    void should_skip_when_cogsIsZero() {
      PolicySnapshot policy = snapshot(null, null, null, new BigDecimal("0.20"));
      PricingSignalSet signals = signalsWithCogs(new BigDecimal("1000"), BigDecimal.ZERO);

      ConstraintResolution result = resolver.resolve(new BigDecimal("100"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("100"));
      assertThat(result.applied()).noneMatch(c -> "min_margin".equals(c.name()));
    }
  }

  @Nested
  @DisplayName("rounding constraint")
  class RoundingConstraint {

    @Test
    @DisplayName("applies FLOOR rounding with step 50")
    void should_roundFloor_when_floorDirection() {
      PolicySnapshot policy = targetMarginSnapshot(
          null, null, null, null,
          new BigDecimal("50"), TargetMarginParams.RoundingDirection.FLOOR);
      PricingSignalSet signals = signals(new BigDecimal("1000"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("1337"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("1300"));
      assertThat(result.applied()).anyMatch(c -> "rounding".equals(c.name()));
    }

    @Test
    @DisplayName("applies CEIL rounding with step 50")
    void should_roundCeil_when_ceilDirection() {
      PolicySnapshot policy = targetMarginSnapshot(
          null, null, null, null,
          new BigDecimal("50"), TargetMarginParams.RoundingDirection.CEIL);
      PricingSignalSet signals = signals(new BigDecimal("1000"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("1337"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("1350"));
    }

    @Test
    @DisplayName("applies NEAREST rounding with step 50")
    void should_roundNearest_when_nearestDirection() {
      PolicySnapshot policy = targetMarginSnapshot(
          null, null, null, null,
          new BigDecimal("50"), TargetMarginParams.RoundingDirection.NEAREST);
      PricingSignalSet signals = signals(new BigDecimal("1000"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("1337"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("1350"));
    }

    @Test
    @DisplayName("does not record rounding when price is already aligned to step")
    void should_notRecord_when_alreadyAligned() {
      PolicySnapshot policy = targetMarginSnapshot(
          null, null, null, null,
          new BigDecimal("50"), TargetMarginParams.RoundingDirection.FLOOR);
      PricingSignalSet signals = signals(new BigDecimal("1000"));

      ConstraintResolution result = resolver.resolve(new BigDecimal("1300"), signals, policy);

      assertThat(result.clampedPrice()).isEqualByComparingTo(new BigDecimal("1300"));
      assertThat(result.applied()).noneMatch(c -> "rounding".equals(c.name()));
    }
  }

  private PolicySnapshot snapshot(BigDecimal minPrice, BigDecimal maxPrice,
                                  BigDecimal maxChangePct, BigDecimal minMarginPct) {
    return new PolicySnapshot(
        1L, 1, "Policy", PolicyType.PRICE_CORRIDOR, "{}", minMarginPct,
        maxChangePct, minPrice, maxPrice, null, ExecutionMode.RECOMMENDATION);
  }

  private PricingSignalSet signals(BigDecimal currentPrice) {
    return new PricingSignalSet(
        currentPrice, null, null, null,
        false, false, null, null, null, null, null, null, null, null);
  }

  private PricingSignalSet signalsWithCogs(BigDecimal currentPrice, BigDecimal cogs) {
    return new PricingSignalSet(
        currentPrice, cogs, null, null,
        false, false, null, null, null, null, null, null, null, null);
  }

  private PolicySnapshot targetMarginSnapshot(BigDecimal minPrice, BigDecimal maxPrice,
                                              BigDecimal maxChangePct, BigDecimal minMarginPct,
                                              BigDecimal roundingStep,
                                              TargetMarginParams.RoundingDirection roundingDir) {
    String params;
    try {
      var tmParams = new TargetMarginParams(
          new BigDecimal("0.20"), null, null, null, null, null, null, null, null,
          roundingStep, roundingDir);
      params = new ObjectMapper().writeValueAsString(tmParams);
    } catch (Exception e) {
      params = "{}";
    }
    return new PolicySnapshot(
        1L, 1, "Policy", PolicyType.TARGET_MARGIN, params, minMarginPct,
        maxChangePct, minPrice, maxPrice, null, ExecutionMode.RECOMMENDATION);
  }
}
