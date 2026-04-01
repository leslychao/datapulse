package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StrategyResultTest {

  @Nested
  @DisplayName("success factory")
  class Success {

    @Test
    @DisplayName("creates result with price and explanation, null reasonKey")
    void should_createSuccess_when_priceProvided() {
      StrategyResult result = StrategyResult.success(
          new BigDecimal("999.99"), "margin calculation");

      assertThat(result.rawTargetPrice()).isEqualByComparingTo(new BigDecimal("999.99"));
      assertThat(result.explanation()).isEqualTo("margin calculation");
      assertThat(result.reasonKey()).isNull();
    }
  }

  @Nested
  @DisplayName("skip factory")
  class Skip {

    @Test
    @DisplayName("creates result with null price and reasonKey set")
    void should_createSkip_when_reasonProvided() {
      StrategyResult result = StrategyResult.skip(
          "COGS not available", "pricing.strategy.cogs_missing");

      assertThat(result.rawTargetPrice()).isNull();
      assertThat(result.explanation()).isEqualTo("COGS not available");
      assertThat(result.reasonKey()).isEqualTo("pricing.strategy.cogs_missing");
    }
  }
}
