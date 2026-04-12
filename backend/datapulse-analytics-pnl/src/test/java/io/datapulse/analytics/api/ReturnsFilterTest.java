package io.datapulse.analytics.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReturnsFilter")
class ReturnsFilterTest {

  @Test
  @DisplayName("periodAsInt should parse yyyy-MM")
  void shouldParsePeriodAsInt() {
    ReturnsFilter filter = new ReturnsFilter(
        LocalDate.of(2025, 3, 1),
        LocalDate.of(2025, 3, 31),
        "2025-03",
        null,
        null,
        TrendGranularity.MONTHLY);

    assertThat(filter.periodAsInt()).isEqualTo(202503);
  }

  @Test
  @DisplayName("periodAsInt should return null for invalid period")
  void shouldReturnNullForInvalidPeriod() {
    ReturnsFilter filter = new ReturnsFilter(null, null, "03-2025", null, null, null);

    assertThat(filter.periodAsInt()).isNull();
  }

  @Test
  @DisplayName("granularity should default to MONTHLY")
  void shouldUseMonthlyGranularityByDefault() {
    ReturnsFilter filter = new ReturnsFilter(null, null, "2025-03", null, null, null);

    assertThat(filter.granularityOrDefault()).isEqualTo(TrendGranularity.MONTHLY);
  }
}
