package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExplanationBuilderTest {

  private ExplanationBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new ExplanationBuilder();
  }

  @Nested
  @DisplayName("buildChange")
  class BuildChange {

    @Test
    @DisplayName("includes decision, policy, strategy, guards, and mode sections")
    void should_includeAllSections_when_fullChangeExplanation() {
      PolicySnapshot policy = snapshot("Target Margin", PolicyType.TARGET_MARGIN, 2);

      String result = builder.buildChange(
          new BigDecimal("1000"), new BigDecimal("1200"), policy,
          "cost / (1 - 0.15) = 1200", List.of(),
          ExecutionMode.FULL_AUTO, "APPROVED");

      assertThat(result).contains("[Решение] CHANGE: 1 000 → 1 200");
      assertThat(result).contains("[Политика] «Target Margin» (TARGET_MARGIN, v2)");
      assertThat(result).contains("[Стратегия] cost / (1 - 0.15) = 1200");
      assertThat(result).contains("[Guards] Все пройдены");
      assertThat(result).contains("[Режим] FULL_AUTO → action APPROVED");
    }

    @Test
    @DisplayName("shows positive change percentage with + sign")
    void should_showPositiveSign_when_priceIncrease() {
      PolicySnapshot policy = snapshot("P", PolicyType.TARGET_MARGIN, 1);

      String result = builder.buildChange(
          new BigDecimal("1000"), new BigDecimal("1100"), policy,
          "explanation", List.of(), ExecutionMode.RECOMMENDATION, "RECOMMENDATION");

      assertThat(result).containsPattern("\\+10[.,]0%");
    }

    @Test
    @DisplayName("shows negative change percentage with − sign")
    void should_showNegativeSign_when_priceDecrease() {
      PolicySnapshot policy = snapshot("P", PolicyType.TARGET_MARGIN, 1);

      String result = builder.buildChange(
          new BigDecimal("1000"), new BigDecimal("900"), policy,
          "explanation", List.of(), ExecutionMode.RECOMMENDATION, "RECOMMENDATION");

      assertThat(result).containsPattern("−10[.,]0%");
    }

    @Test
    @DisplayName("includes constraints section when constraints applied")
    void should_includeConstraints_when_constraintsApplied() {
      PolicySnapshot policy = snapshot("P", PolicyType.TARGET_MARGIN, 1);
      List<ConstraintRecord> constraints = List.of(
          new ConstraintRecord("min_price", new BigDecimal("800"), new BigDecimal("900")),
          new ConstraintRecord("rounding", new BigDecimal("917"), new BigDecimal("910")));

      String result = builder.buildChange(
          new BigDecimal("1000"), new BigDecimal("910"), policy,
          "strategy", constraints, ExecutionMode.FULL_AUTO, "APPROVED");

      assertThat(result).contains("[Ограничения] min_price: 800 → 900; rounding: 917 → 910");
    }

    @Test
    @DisplayName("handles null currentPrice gracefully")
    void should_showDash_when_currentPriceNull() {
      PolicySnapshot policy = snapshot("P", PolicyType.TARGET_MARGIN, 1);

      String result = builder.buildChange(
          null, new BigDecimal("1200"), policy,
          "strategy", List.of(), ExecutionMode.FULL_AUTO, "APPROVED");

      assertThat(result).contains("— → 1 200");
    }

    @Test
    @DisplayName("handles zero currentPrice without division error")
    void should_showZeroPercent_when_currentPriceZero() {
      PolicySnapshot policy = snapshot("P", PolicyType.TARGET_MARGIN, 1);

      String result = builder.buildChange(
          BigDecimal.ZERO, new BigDecimal("500"), policy,
          "strategy", List.of(), ExecutionMode.FULL_AUTO, "APPROVED");

      assertThat(result).containsPattern("0[.,]0%");
    }
  }

  @Nested
  @DisplayName("buildSkip")
  class BuildSkip {

    @Test
    @DisplayName("includes reason without guard details when guard is null")
    void should_skipWithReason_when_noGuard() {
      String result = builder.buildSkip("pricing.strategy.no_change", null, null);

      assertThat(result).contains("[Решение] SKIP");
      assertThat(result).contains("[Причина] pricing.strategy.no_change");
      assertThat(result).doesNotContain("[Guard]");
    }

    @Test
    @DisplayName("includes guard details when guard name is provided")
    void should_includeGuard_when_guardNameProvided() {
      String result = builder.buildSkip("pricing.guard.manual_lock",
          "manual_lock_guard", "Offer is locked");

      assertThat(result).contains("[Guard] manual_lock_guard: Offer is locked");
    }
  }

  @Nested
  @DisplayName("buildHold")
  class BuildHold {

    @Test
    @DisplayName("includes policy info when policy is provided")
    void should_includePolicy_when_policyProvided() {
      PolicySnapshot policy = snapshot("Margin Policy", PolicyType.TARGET_MARGIN, 3);

      String result = builder.buildHold("COGS missing", policy);

      assertThat(result).contains("[Решение] HOLD");
      assertThat(result).contains("[Причина] COGS missing");
      assertThat(result).contains("[Политика] «Margin Policy» (TARGET_MARGIN, v3)");
    }

    @Test
    @DisplayName("omits policy section when policy is null")
    void should_omitPolicy_when_policyNull() {
      String result = builder.buildHold("No data", null);

      assertThat(result).contains("[Решение] HOLD");
      assertThat(result).doesNotContain("[Политика]");
    }
  }

  @Nested
  @DisplayName("buildSkipGuard")
  class BuildSkipGuard {

    @Test
    @DisplayName("includes policy and blocking guard details")
    void should_includeAll_when_guardBlocks() {
      PolicySnapshot policy = snapshot("P", PolicyType.TARGET_MARGIN, 1);
      GuardResult blocking = GuardResult.block("stale_data_guard",
          "pricing.guard.stale_data.stale");

      String result = builder.buildSkipGuard(policy, blocking);

      assertThat(result).contains("[Решение] SKIP");
      assertThat(result).contains("[Причина] pricing.guard.stale_data.stale");
      assertThat(result).contains("[Политика] «P» (TARGET_MARGIN, v1)");
      assertThat(result).contains("[Guard] stale_data_guard: pricing.guard.stale_data.stale");
    }
  }

  private PolicySnapshot snapshot(String name, PolicyType type, int version) {
    return new PolicySnapshot(1L, version, name, type, "{}", null, null, null, null, null,
        ExecutionMode.RECOMMENDATION);
  }
}
