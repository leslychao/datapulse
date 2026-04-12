package io.datapulse.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.datapulse.analytics.api.ProductReturnResponse;
import io.datapulse.analytics.api.ReturnsFilter;
import io.datapulse.analytics.api.ReturnsSummaryResponse;
import io.datapulse.analytics.api.TrendGranularity;
import io.datapulse.analytics.api.ReturnsTrendResponse;
import io.datapulse.analytics.persistence.ReturnsReadRepository;
import io.datapulse.analytics.persistence.ReturnsReadRepository.ReasonRow;
import io.datapulse.analytics.persistence.ReturnsReadRepository.SummaryRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnsAnalysisService")
class ReturnsAnalysisServiceTest {

  @Mock private ReturnsReadRepository returnsReadRepository;

  @InjectMocks
  private ReturnsAnalysisService service;

  private static final long WORKSPACE_ID = 1L;
  private static final ReturnsFilter EMPTY_FILTER =
      new ReturnsFilter(null, null, null, null, null, null);

  @Nested
  @DisplayName("getSummary")
  class GetSummary {

    @Test
    @DisplayName("should include reason breakdown and delta for period filter")
    void should_includeReasonBreakdownAndDelta_when_periodProvided() {
      var filter = new ReturnsFilter(null, null, "2025-03", null, null, null);
      var summary = new SummaryRow(
          20, 30, new BigDecimal("15000.00"),
          100, 200, new BigDecimal("15.00"), "Damaged", 5);

      when(returnsReadRepository.findSummary(WORKSPACE_ID, filter)).thenReturn(summary);
      when(returnsReadRepository.findReasonBreakdown(eq(WORKSPACE_ID), eq(202503), eq(filter)))
          .thenReturn(List.of(
              new ReasonRow("Damaged", 6, new BigDecimal("9000.00"), 3),
              new ReasonRow("Wrong size", 4, new BigDecimal("6000.00"), 2)));
      when(returnsReadRepository.findReturnRateForPeriod(eq(WORKSPACE_ID), eq(202502), eq(filter)))
          .thenReturn(new BigDecimal("12.00"));

      ReturnsSummaryResponse result = service.getSummary(WORKSPACE_ID, filter);

      assertThat(result.returnRatePct()).isEqualByComparingTo("15.00");
      assertThat(result.returnRateDeltaPct()).isEqualByComparingTo("3.00");
      assertThat(result.reasonBreakdown()).hasSize(2);
      assertThat(result.reasonBreakdown().get(0).reason()).isEqualTo("Damaged");
      assertThat(result.reasonBreakdown().get(0).percent()).isEqualByComparingTo("60.0");
    }

    @Test
    @DisplayName("should skip reason breakdown and delta when period absent")
    void should_skipPeriodSpecificData_when_periodMissing() {
      var summary = new SummaryRow(
          2, 3, new BigDecimal("1200.00"),
          10, 20, new BigDecimal("15.00"), "Damaged", 2);

      when(returnsReadRepository.findSummary(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(summary);

      ReturnsSummaryResponse result = service.getSummary(WORKSPACE_ID, EMPTY_FILTER);

      assertThat(result.returnRateDeltaPct()).isNull();
      assertThat(result.reasonBreakdown()).isEmpty();
    }
  }

  @Nested
  @DisplayName("getByProduct")
  class GetByProduct {

    @Test
    @DisplayName("should use 'returnRatePct' as default sort column")
    void should_useDefaultSort_when_noSortProvided() {
      when(returnsReadRepository.countByProduct(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(0L);
      when(returnsReadRepository.findByProduct(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          eq("returnRatePct"), eq("DESC"), eq(20), eq(0L)))
          .thenReturn(List.of());

      service.getByProduct(WORKSPACE_ID, EMPTY_FILTER, PageRequest.of(0, 20));

      verify(returnsReadRepository).findByProduct(WORKSPACE_ID, EMPTY_FILTER,
          "returnRatePct", "DESC", 20, 0L);
    }

    @Test
    @DisplayName("should extract custom sort column from pageable")
    void should_extractSort_when_sortProvided() {
      when(returnsReadRepository.countByProduct(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(0L);
      when(returnsReadRepository.findByProduct(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          eq("returnQuantity"), eq("ASC"), eq(10), eq(0L)))
          .thenReturn(List.of());

      service.getByProduct(WORKSPACE_ID, EMPTY_FILTER,
          PageRequest.of(0, 10, Sort.by("returnQuantity")));

      verify(returnsReadRepository).findByProduct(WORKSPACE_ID, EMPTY_FILTER,
          "returnQuantity", "ASC", 10, 0L);
    }

    @Test
    @DisplayName("should return page content from repository")
    void should_includeReasons_when_productReturnsRetrieved() {
      var product = new ProductReturnResponse(
          "WB", 100L, 1L, "SKU001", "Product A", 202501,
          10, 12, new BigDecimal("6000.00"), 100, 120,
          new BigDecimal("10.00"),
          "Wrong size", 2);

      when(returnsReadRepository.findByProduct(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          eq("returnRatePct"), eq("DESC"), eq(20), eq(0L)))
          .thenReturn(List.of(product));
      when(returnsReadRepository.countByProduct(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(1L);

      Page<ProductReturnResponse> result = service.getByProduct(
          WORKSPACE_ID, EMPTY_FILTER, PageRequest.of(0, 20));

      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).topReturnReason()).isEqualTo("Wrong size");
      assertThat(result.getContent().get(0).returnRatePct())
          .isEqualByComparingTo("10.00");
    }
  }

  @Nested
  @DisplayName("getTrend")
  class GetTrend {

    @Test
    @DisplayName("should build month bounds from period when from/to absent")
    void should_buildBoundsFromPeriod_when_fromToMissing() {
      var filter = new ReturnsFilter(null, null, "2025-03", null, null, TrendGranularity.DAILY);
      var trend = new ReturnsTrendResponse("2025-03-10", 2, 10, new BigDecimal("20.00"));
      when(returnsReadRepository.findTrend(
          eq(WORKSPACE_ID),
          eq(new ReturnsFilter(
              LocalDate.of(2025, 3, 1),
              LocalDate.of(2025, 3, 31),
              "2025-03",
              null,
              null,
              TrendGranularity.DAILY)),
          eq(TrendGranularity.DAILY)))
          .thenReturn(List.of(trend));

      List<ReturnsTrendResponse> result = service.getTrend(WORKSPACE_ID, filter);

      assertThat(result).hasSize(1);
      verify(returnsReadRepository).findTrend(
          eq(WORKSPACE_ID),
          eq(new ReturnsFilter(
              LocalDate.of(2025, 3, 1),
              LocalDate.of(2025, 3, 31),
              "2025-03",
              null,
              null,
              TrendGranularity.DAILY)),
          eq(TrendGranularity.DAILY));
    }

    @Test
    @DisplayName("should keep explicit date range as is")
    void should_keepExplicitRange_when_fromToProvided() {
      var filter = new ReturnsFilter(
          LocalDate.of(2025, 4, 1),
          LocalDate.of(2025, 4, 30),
          null,
          null,
          null,
          TrendGranularity.MONTHLY);
      when(returnsReadRepository.findTrend(
          eq(WORKSPACE_ID), eq(filter), eq(TrendGranularity.MONTHLY)))
          .thenReturn(List.of());

      service.getTrend(WORKSPACE_ID, filter);

      verify(returnsReadRepository).findTrend(
          eq(WORKSPACE_ID), eq(filter), eq(TrendGranularity.MONTHLY));
    }
  }
}
