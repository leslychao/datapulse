package io.datapulse.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MaterializationPhase")
class MaterializationPhaseTest {

  @Test
  @DisplayName("should define exactly three phases")
  void should_haveThreePhases() {
    assertThat(MaterializationPhase.values()).hasSize(3);
  }

  @Test
  @DisplayName("should maintain DIMENSION → FACT → MART ordinal order")
  void should_maintainOrdinalOrder() {
    assertThat(MaterializationPhase.DIMENSION.ordinal())
        .isLessThan(MaterializationPhase.FACT.ordinal());
    assertThat(MaterializationPhase.FACT.ordinal())
        .isLessThan(MaterializationPhase.MART.ordinal());
  }

  @Test
  @DisplayName("DIMENSION should be the first phase (ordinal = 0)")
  void should_startWithDimension() {
    assertThat(MaterializationPhase.DIMENSION.ordinal()).isZero();
  }
}
