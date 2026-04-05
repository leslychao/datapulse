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

class StaleDataGuardTest {

  private final StaleDataGuard guard = new StaleDataGuard();

  @Test
  @DisplayName("guard name is 'stale_data_guard'")
  void should_returnCorrectName() {
    assertThat(guard.guardName()).isEqualTo("stale_data_guard");
  }

  @Test
  @DisplayName("order is 20")
  void should_returnOrder20() {
    assertThat(guard.order()).isEqualTo(20);
  }

  @Nested
  @DisplayName("check")
  class Check {

    @Test
    @DisplayName("blocks when data freshness is unknown (null)")
    void should_block_when_dataFreshnessNull() {
      PricingSignalSet signals = signalsWithFreshness(null);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isFalse();
      assertThat(result.reason()).isEqualTo("pricing.guard.stale_data.unknown");
    }

    @Test
    @DisplayName("blocks when data is stale (older than threshold)")
    void should_block_when_dataStale() {
      OffsetDateTime staleTime = OffsetDateTime.now().minusHours(48);
      PricingSignalSet signals = signalsWithFreshness(staleTime);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isFalse();
      assertThat(result.reason()).isEqualTo("pricing.guard.stale_data.stale");
      assertThat(result.args()).containsKey("hours");
      assertThat(result.args()).containsKey("lastSync");
    }

    @Test
    @DisplayName("passes when data is fresh (within threshold)")
    void should_pass_when_dataFresh() {
      OffsetDateTime freshTime = OffsetDateTime.now().minusHours(1);
      PricingSignalSet signals = signalsWithFreshness(freshTime);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("respects custom threshold from config")
    void should_useCustomThreshold_when_configured() {
      OffsetDateTime time = OffsetDateTime.now().minusHours(5);
      PricingSignalSet signals = signalsWithFreshness(time);

      GuardConfig strictConfig = new GuardConfig(
          null, null, null, null, null, null, null, null, 4, null, null);

      GuardResult result = guard.check(signals, BigDecimal.TEN, strictConfig);

      assertThat(result.passed()).isFalse();
    }

    @Test
    @DisplayName("passes with custom relaxed threshold")
    void should_pass_when_withinRelaxedThreshold() {
      OffsetDateTime time = OffsetDateTime.now().minusHours(30);
      PricingSignalSet signals = signalsWithFreshness(time);

      GuardConfig relaxedConfig = new GuardConfig(
          null, null, null, null, null, null, null, null, 48, null, null);

      GuardResult result = guard.check(signals, BigDecimal.TEN, relaxedConfig);

      assertThat(result.passed()).isTrue();
    }
  }

  @Nested
  @DisplayName("boundary conditions")
  class BoundaryConditions {

    @Test
    @DisplayName("passes when data is exactly at threshold boundary")
    void should_pass_when_exactlyAtBoundary() {
      OffsetDateTime exactBoundary = OffsetDateTime.now().minusHours(23).minusMinutes(59);
      PricingSignalSet signals = signalsWithFreshness(exactBoundary);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("blocks when data is 1 minute past threshold")
    void should_block_when_justPastBoundary() {
      OffsetDateTime justPast = OffsetDateTime.now().minusHours(24).minusMinutes(1);
      PricingSignalSet signals = signalsWithFreshness(justPast);

      GuardResult result = guard.check(signals, BigDecimal.TEN, GuardConfig.DEFAULTS);

      assertThat(result.passed()).isFalse();
    }
  }

  private PricingSignalSet signalsWithFreshness(OffsetDateTime dataFreshnessAt) {
    return new PricingSignalSet(
        new BigDecimal("1000"), null, null, null,
        false, false,
        null, null, null, null, null, null, dataFreshnessAt, null);
  }
}
