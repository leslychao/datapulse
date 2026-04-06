package io.datapulse.pricing.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardResult;
import io.datapulse.pricing.domain.PricingSignalSet;

class CompetitorFreshnessGuardTest {

  private final CompetitorFreshnessGuard guard = new CompetitorFreshnessGuard();

  @Test
  @DisplayName("guard name is 'competitor_freshness_guard'")
  void should_returnCorrectName() {
    assertThat(guard.guardName()).isEqualTo("competitor_freshness_guard");
  }

  @Test
  @DisplayName("order is 25")
  void should_returnOrder25() {
    assertThat(guard.order()).isEqualTo(25);
  }

  @Nested
  @DisplayName("when guard enabled")
  class Enabled {

    @Test
    @DisplayName("passes when competitor data is fresh")
    void should_pass_when_dataFresh() {
      PricingSignalSet signals = signalsWithFreshness(
          OffsetDateTime.now().minusHours(12));

      GuardResult result = guard.check(signals, new BigDecimal("1000"),
          configEnabled(72));

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("blocks when competitor data is stale")
    void should_block_when_dataStale() {
      PricingSignalSet signals = signalsWithFreshness(
          OffsetDateTime.now().minusHours(100));

      GuardResult result = guard.check(signals, new BigDecimal("1000"),
          configEnabled(72));

      assertThat(result.passed()).isFalse();
      assertThat(result.reason()).isEqualTo("pricing.competitor.stale");
      assertThat(result.args()).containsKey("hours");
      assertThat(result.args()).containsKey("lastObserved");
    }

    @Test
    @DisplayName("passes when competitor freshness is null (no competitor data)")
    void should_pass_when_freshnessNull() {
      PricingSignalSet signals = signalsWithFreshness(null);

      GuardResult result = guard.check(signals, new BigDecimal("1000"),
          configEnabled(72));

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("blocks when data is exactly at threshold boundary")
    void should_block_when_dataExactlyAtThreshold() {
      PricingSignalSet signals = signalsWithFreshness(
          OffsetDateTime.now().minusHours(73));

      GuardResult result = guard.check(signals, new BigDecimal("1000"),
          configEnabled(72));

      assertThat(result.passed()).isFalse();
    }
  }

  @Nested
  @DisplayName("when guard disabled")
  class Disabled {

    @Test
    @DisplayName("passes even with stale data")
    void should_pass_when_disabled() {
      PricingSignalSet signals = signalsWithFreshness(
          OffsetDateTime.now().minusDays(30));

      GuardResult result = guard.check(signals, new BigDecimal("1000"),
          GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
    }
  }

  private PricingSignalSet signalsWithFreshness(OffsetDateTime freshnessAt) {
    return new PricingSignalSet(
        new BigDecimal("1000"), null, null, null,
        false, false,
        null, null, null, null, null, null, null, null,
        null, null, null, null, null,
        new BigDecimal("900"), "TRUSTED", freshnessAt);
  }

  private GuardConfig configEnabled(int hours) {
    return new GuardConfig(
        null, null, null, null, null, null, null, null, null,
        null, null, true, hours, null, null);
  }
}
