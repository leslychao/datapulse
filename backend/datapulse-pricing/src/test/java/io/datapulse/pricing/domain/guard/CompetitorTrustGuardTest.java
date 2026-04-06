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

class CompetitorTrustGuardTest {

  private final CompetitorTrustGuard guard = new CompetitorTrustGuard();

  @Test
  @DisplayName("guard name is 'competitor_trust_guard'")
  void should_returnCorrectName() {
    assertThat(guard.guardName()).isEqualTo("competitor_trust_guard");
  }

  @Test
  @DisplayName("order is 26")
  void should_returnOrder26() {
    assertThat(guard.order()).isEqualTo(26);
  }

  @Nested
  @DisplayName("when guard enabled")
  class Enabled {

    @Test
    @DisplayName("passes when trust level is TRUSTED")
    void should_pass_when_trusted() {
      PricingSignalSet signals = signalsWithTrust("TRUSTED");

      GuardResult result = guard.check(signals, new BigDecimal("1000"),
          configEnabled());

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("blocks when trust level is CANDIDATE")
    void should_block_when_candidate() {
      PricingSignalSet signals = signalsWithTrust("CANDIDATE");

      GuardResult result = guard.check(signals, new BigDecimal("1000"),
          configEnabled());

      assertThat(result.passed()).isFalse();
      assertThat(result.reason()).isEqualTo("pricing.competitor.untrusted");
      assertThat(result.args()).containsEntry("trustLevel", "CANDIDATE");
    }

    @Test
    @DisplayName("passes when trust level is null (no competitor data)")
    void should_pass_when_trustLevelNull() {
      PricingSignalSet signals = signalsWithTrust(null);

      GuardResult result = guard.check(signals, new BigDecimal("1000"),
          configEnabled());

      assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("passes when trust level is REJECTED (guard only blocks CANDIDATE)")
    void should_pass_when_rejected() {
      PricingSignalSet signals = signalsWithTrust("REJECTED");

      GuardResult result = guard.check(signals, new BigDecimal("1000"),
          configEnabled());

      assertThat(result.passed()).isTrue();
    }
  }

  @Nested
  @DisplayName("when guard disabled")
  class Disabled {

    @Test
    @DisplayName("passes even with CANDIDATE trust level")
    void should_pass_when_disabled() {
      PricingSignalSet signals = signalsWithTrust("CANDIDATE");

      GuardResult result = guard.check(signals, new BigDecimal("1000"),
          GuardConfig.DEFAULTS);

      assertThat(result.passed()).isTrue();
    }
  }

  private PricingSignalSet signalsWithTrust(String trustLevel) {
    return new PricingSignalSet(
        new BigDecimal("1000"), null, null, null,
        false, false,
        null, null, null, null, null, null, null, null,
        null, null, null, null, null,
        new BigDecimal("900"), trustLevel, OffsetDateTime.now());
  }

  private GuardConfig configEnabled() {
    return new GuardConfig(
        null, null, null, null, null, null, null, null, null,
        null, null, null, null, true, null);
  }
}
