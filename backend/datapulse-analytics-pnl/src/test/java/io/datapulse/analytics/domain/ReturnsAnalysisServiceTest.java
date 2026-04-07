package io.datapulse.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import io.datapulse.analytics.api.ProductReturnResponse;
import io.datapulse.analytics.api.ReturnsFilter;
import io.datapulse.analytics.api.ReturnsSummaryResponse;
import io.datapulse.analytics.api.ReturnsTrendResponse;
import io.datapulse.analytics.persistence.ReturnsReadRepository;
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
  private static final ReturnsFilter EMPTY_FILTER = new ReturnsFilter(null, null);

  @Nested
  @DisplayName("getSummary")
  class GetSummary {

    @Test
    @DisplayName("should return summary with return rate calculation")
    void should_returnSummary() {
      var summary = new ReturnsSummaryResponse(
          "WB", 25, 30, new BigDecimal("15000.00"),
          500, 600, new BigDecimal("5.00"),
          new BigDecimal("-12000.00"), new BigDecimal("-500.00"),
          "Defective product");

      when(returnsReadRepository.findSummary(WORKSPACE_ID, EMPTY_FILTER))
          .thenReturn(List.of(summary));

      List<ReturnsSummaryResponse> result = service.getSummary(WORKSPACE_ID, EMPTY_FILTER);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).returnRatePct()).isEqualByComparingTo("5.00");
      assertThat(result.get(0).topReturnReason()).isEqualTo("Defective product");
    }

    @Test
    @DisplayName("should handle zero return rate when no returns")
    void should_returnZeroRate_when_noReturns() {
      var summary = new ReturnsSummaryResponse(
          "WB", 0, 0, BigDecimal.ZERO,
          500, 600, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, null);

      when(returnsReadRepository.findSummary(WORKSPACE_ID, EMPTY_FILTER))
          .thenReturn(List.of(summary));

      List<ReturnsSummaryResponse> result = service.getSummary(WORKSPACE_ID, EMPTY_FILTER);

      assertThat(result.get(0).returnRatePct()).isEqualByComparingTo(BigDecimal.ZERO);
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
          eq("returnRatePct"), eq(20), eq(0L)))
          .thenReturn(List.of());

      service.getByProduct(WORKSPACE_ID, EMPTY_FILTER, PageRequest.of(0, 20));

      verify(returnsReadRepository).findByProduct(WORKSPACE_ID, EMPTY_FILTER,
          "returnRatePct", 20, 0L);
    }

    @Test
    @DisplayName("should extract custom sort column from pageable")
    void should_extractSort_when_sortProvided() {
      when(returnsReadRepository.countByProduct(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(0L);
      when(returnsReadRepository.findByProduct(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          eq("returnQuantity"), eq(10), eq(0L)))
          .thenReturn(List.of());

      service.getByProduct(WORKSPACE_ID, EMPTY_FILTER,
          PageRequest.of(0, 10, Sort.by("returnQuantity")));

      verify(returnsReadRepository).findByProduct(WORKSPACE_ID, EMPTY_FILTER,
          "returnQuantity", 10, 0L);
    }

    @Test
    @DisplayName("should include reasons breakdown in product response")
    void should_includeReasons_when_productReturnsRetrieved() {
      var product = new ProductReturnResponse(
          "WB", 100L, 1L, "SKU001", "Product A",
          202501, 10, 12, new BigDecimal("6000.00"),
          100, new BigDecimal("12.00"),
          new BigDecimal("-5000.00"), new BigDecimal("-200.00"),
          "Wrong size");

      when(returnsReadRepository.findByProduct(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          anyString(), eq(20), eq(0L)))
          .thenReturn(List.of(product));
      when(returnsReadRepository.countByProduct(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(1L);

      Page<ProductReturnResponse> result = service.getByProduct(
          WORKSPACE_ID, EMPTY_FILTER, PageRequest.of(0, 20));

      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).topReturnReason()).isEqualTo("Wrong size");
      assertThat(result.getContent().get(0).returnRatePct())
          .isEqualByComparingTo("12.00");
    }
  }

  @Nested
  @DisplayName("getTrend")
  class GetTrend {

    @Test
    @DisplayName("should delegate to repository and return trend data")
    void should_returnTrend() {
      var trend = new ReturnsTrendResponse(
          202501, 30, 600, new BigDecimal("5.00"), new BigDecimal("-12000.00"));
      when(returnsReadRepository.findTrend(WORKSPACE_ID, EMPTY_FILTER))
          .thenReturn(List.of(trend));

      List<ReturnsTrendResponse> result = service.getTrend(WORKSPACE_ID, EMPTY_FILTER);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).returnRatePct()).isEqualByComparingTo("5.00");
    }
  }
}
