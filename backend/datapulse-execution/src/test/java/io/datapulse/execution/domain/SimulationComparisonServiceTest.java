package io.datapulse.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.execution.persistence.SimulationComparisonRepository;
import io.datapulse.execution.persistence.SimulationComparisonRepository.CoverageRow;
import io.datapulse.execution.persistence.SimulationComparisonRepository.SimulationComparisonRow;
import io.datapulse.execution.persistence.SimulationComparisonRepository.SimulationSummaryRow;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationComparisonService")
class SimulationComparisonServiceTest {

  @Mock private SimulationComparisonRepository comparisonRepository;

  @InjectMocks
  private SimulationComparisonService service;

  private static final long WORKSPACE_ID = 10L;
  private static final long CONNECTION_ID = 5L;

  @Nested
  @DisplayName("buildReport")
  class BuildReport {

    @Test
    @DisplayName("should build report with coverage percentage")
    void should_buildReport_when_dataExists() {
      var summary = new SimulationSummaryRow(
          50, BigDecimal.valueOf(5.25), 30, 15, 5, BigDecimal.valueOf(12500));
      var coverage = new CoverageRow(50, 200);
      var items = List.of(comparisonRow(BigDecimal.valueOf(999), BigDecimal.valueOf(950)));

      when(comparisonRepository.findSummary(WORKSPACE_ID, CONNECTION_ID))
          .thenReturn(summary);
      when(comparisonRepository.findCoverage(WORKSPACE_ID, CONNECTION_ID))
          .thenReturn(coverage);
      when(comparisonRepository.findComparisonItems(WORKSPACE_ID, CONNECTION_ID))
          .thenReturn(items);

      var report = service.buildReport(WORKSPACE_ID, CONNECTION_ID);

      assertThat(report.connectionId()).isEqualTo(CONNECTION_ID);
      assertThat(report.totalSimulatedActions()).isEqualTo(50);
      assertThat(report.avgDeltaPct()).isEqualByComparingTo(BigDecimal.valueOf(5.25));
      assertThat(report.countIncrease()).isEqualTo(30);
      assertThat(report.countDecrease()).isEqualTo(15);
      assertThat(report.countUnchanged()).isEqualTo(5);
      assertThat(report.simulatedOfferCount()).isEqualTo(50);
      assertThat(report.totalOfferCount()).isEqualTo(200);
      assertThat(report.coveragePct()).isEqualByComparingTo(BigDecimal.valueOf(25));
      assertThat(report.items()).hasSize(1);
    }

    @Test
    @DisplayName("should return zero coverage when no offers")
    void should_returnZeroCoverage_when_noOffers() {
      var summary = new SimulationSummaryRow(
          0, BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO);
      var coverage = new CoverageRow(0, 0);

      when(comparisonRepository.findSummary(WORKSPACE_ID, CONNECTION_ID))
          .thenReturn(summary);
      when(comparisonRepository.findCoverage(WORKSPACE_ID, CONNECTION_ID))
          .thenReturn(coverage);
      when(comparisonRepository.findComparisonItems(WORKSPACE_ID, CONNECTION_ID))
          .thenReturn(List.of());

      var report = service.buildReport(WORKSPACE_ID, CONNECTION_ID);

      assertThat(report.coveragePct()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(report.totalSimulatedActions()).isZero();
    }
  }

  @Nested
  @DisplayName("previewByDecision")
  class PreviewByDecision {

    @Test
    @DisplayName("should return comparison rows for decision")
    void should_returnRows_when_decisionExists() {
      var row = comparisonRow(BigDecimal.valueOf(999), BigDecimal.valueOf(950));
      when(comparisonRepository.findByDecision(WORKSPACE_ID, 42L))
          .thenReturn(List.of(row));

      var result = service.previewByDecision(WORKSPACE_ID, 42L);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).simulatedPrice())
          .isEqualByComparingTo(BigDecimal.valueOf(999));
    }
  }

  private SimulationComparisonRow comparisonRow(BigDecimal simulated, BigDecimal canonical) {
    return new SimulationComparisonRow(
        100L, "SKU-123", simulated, canonical,
        BigDecimal.valueOf(940),
        simulated.subtract(canonical),
        BigDecimal.valueOf(5.16),
        null, OffsetDateTime.now(), 1L);
  }
}
