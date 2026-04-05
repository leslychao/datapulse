package io.datapulse.integration.domain.ratelimit;

import io.datapulse.integration.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

class AimdRateAdjusterTest {

  private RateLimitProperties properties;
  private AimdRateAdjuster controller;

  @BeforeEach
  void setUp() {
    properties = new RateLimitProperties();
    properties.setIncreasePct(0.2);
    properties.setDecreaseFactor(0.5);
    properties.setStabilityWindow(3);
    properties.setMinRate(0.01);
    properties.setGroups(new HashMap<>());
    controller = new AimdRateAdjuster(properties);
  }

  @Nested
  @DisplayName("getCurrentRate")
  class GetCurrentRate {

    @Test
    @DisplayName("should_return_initial_rate_when_no_state_exists")
    void should_return_initial_rate_when_no_state_exists() {
      double rate = controller.getCurrentRate(1L, RateLimitGroup.WB_CONTENT);

      assertThat(rate).isEqualTo(RateLimitGroup.WB_CONTENT.getInitialRate());
    }

    @Test
    @DisplayName("should_return_override_initial_rate_when_configured")
    void should_return_override_initial_rate_when_configured() {
      var override = new RateLimitProperties.GroupOverride();
      override.setInitialRate(5.0);
      properties.getGroups().put(RateLimitGroup.WB_CONTENT.name(), override);

      double rate = controller.getCurrentRate(1L, RateLimitGroup.WB_CONTENT);

      assertThat(rate).isEqualTo(5.0);
    }
  }

  @Nested
  @DisplayName("onThrottle")
  class OnThrottle {

    @Test
    @DisplayName("should_decrease_rate_by_factor_when_throttled")
    void should_decrease_rate_by_factor_when_throttled() {
      double initialRate = RateLimitGroup.WB_CONTENT.getInitialRate();

      controller.onThrottle(1L, RateLimitGroup.WB_CONTENT);

      double newRate = controller.getCurrentRate(1L, RateLimitGroup.WB_CONTENT);
      assertThat(newRate).isEqualTo(Math.max(initialRate * 0.5, properties.getMinRate()));
    }

    @Test
    @DisplayName("should_not_go_below_min_rate_when_throttled_multiple_times")
    void should_not_go_below_min_rate_when_throttled_multiple_times() {
      for (int i = 0; i < 50; i++) {
        controller.onThrottle(1L, RateLimitGroup.WB_CONTENT);
      }

      double rate = controller.getCurrentRate(1L, RateLimitGroup.WB_CONTENT);
      assertThat(rate).isGreaterThanOrEqualTo(properties.getMinRate());
    }

    @Test
    @DisplayName("should_use_override_min_rate_when_configured")
    void should_use_override_min_rate_when_configured() {
      var override = new RateLimitProperties.GroupOverride();
      override.setMinRate(0.5);
      properties.getGroups().put(RateLimitGroup.WB_CONTENT.name(), override);

      for (int i = 0; i < 50; i++) {
        controller.onThrottle(1L, RateLimitGroup.WB_CONTENT);
      }

      double rate = controller.getCurrentRate(1L, RateLimitGroup.WB_CONTENT);
      assertThat(rate).isGreaterThanOrEqualTo(0.5);
    }

    @Test
    @DisplayName("should_reset_consecutive_successes_when_throttled")
    void should_reset_consecutive_successes_when_throttled() {
      for (int i = 0; i < 2; i++) {
        controller.onSuccess(1L, RateLimitGroup.WB_CONTENT);
      }
      controller.onThrottle(1L, RateLimitGroup.WB_CONTENT);

      double rateAfterThrottle = controller.getCurrentRate(1L, RateLimitGroup.WB_CONTENT);

      // stabilityWindow=3, so 3 more successes needed to increase
      for (int i = 0; i < 3; i++) {
        controller.onSuccess(1L, RateLimitGroup.WB_CONTENT);
      }

      double rateAfterRecovery = controller.getCurrentRate(1L, RateLimitGroup.WB_CONTENT);
      assertThat(rateAfterRecovery).isGreaterThan(rateAfterThrottle);
    }
  }

  @Nested
  @DisplayName("onSuccess")
  class OnSuccess {

    @Test
    @DisplayName("should_increase_rate_after_stability_window_reached")
    void should_increase_rate_after_stability_window_reached() {
      double initialRate = RateLimitGroup.WB_CONTENT.getInitialRate();

      for (int i = 0; i < properties.getStabilityWindow(); i++) {
        controller.onSuccess(1L, RateLimitGroup.WB_CONTENT);
      }

      double newRate = controller.getCurrentRate(1L, RateLimitGroup.WB_CONTENT);
      assertThat(newRate).isGreaterThan(initialRate);
    }

    @Test
    @DisplayName("should_not_increase_before_stability_window")
    void should_not_increase_before_stability_window() {
      for (int i = 0; i < properties.getStabilityWindow() - 1; i++) {
        controller.onSuccess(1L, RateLimitGroup.WB_CONTENT);
      }

      double rate = controller.getCurrentRate(1L, RateLimitGroup.WB_CONTENT);
      assertThat(rate).isEqualTo(RateLimitGroup.WB_CONTENT.getInitialRate());
    }

    @Test
    @DisplayName("should_not_exceed_max_rate_when_many_successes")
    void should_not_exceed_max_rate_when_many_successes() {
      double maxRate = RateLimitGroup.WB_CONTENT.getInitialRate() * 2.0;

      for (int i = 0; i < 200; i++) {
        controller.onSuccess(1L, RateLimitGroup.WB_CONTENT);
      }

      double rate = controller.getCurrentRate(1L, RateLimitGroup.WB_CONTENT);
      assertThat(rate).isLessThanOrEqualTo(maxRate);
    }

    @Test
    @DisplayName("should_use_override_max_rate_when_configured")
    void should_use_override_max_rate_when_configured() {
      var override = new RateLimitProperties.GroupOverride();
      override.setMaxRate(0.2);
      properties.getGroups().put(RateLimitGroup.WB_CONTENT.name(), override);

      for (int i = 0; i < 200; i++) {
        controller.onSuccess(1L, RateLimitGroup.WB_CONTENT);
      }

      double rate = controller.getCurrentRate(1L, RateLimitGroup.WB_CONTENT);
      assertThat(rate).isLessThanOrEqualTo(0.2);
    }
  }

  @Nested
  @DisplayName("resolveBurst")
  class ResolveBurst {

    @Test
    @DisplayName("should_return_default_burst_when_no_override")
    void should_return_default_burst_when_no_override() {
      int burst = controller.resolveBurst(RateLimitGroup.OZON_DEFAULT);
      assertThat(burst).isEqualTo(RateLimitGroup.OZON_DEFAULT.getBurst());
    }

    @Test
    @DisplayName("should_return_override_burst_when_configured")
    void should_return_override_burst_when_configured() {
      var override = new RateLimitProperties.GroupOverride();
      override.setBurst(10);
      properties.getGroups().put(RateLimitGroup.OZON_DEFAULT.name(), override);

      int burst = controller.resolveBurst(RateLimitGroup.OZON_DEFAULT);
      assertThat(burst).isEqualTo(10);
    }
  }

  @Nested
  @DisplayName("isolation")
  class Isolation {

    @Test
    @DisplayName("should_track_rates_independently_per_connection_and_group")
    void should_track_rates_independently_per_connection_and_group() {
      controller.onThrottle(1L, RateLimitGroup.WB_CONTENT);

      double conn1Rate = controller.getCurrentRate(1L, RateLimitGroup.WB_CONTENT);
      double conn2Rate = controller.getCurrentRate(2L, RateLimitGroup.WB_CONTENT);

      assertThat(conn1Rate).isLessThan(conn2Rate);
    }
  }
}
