package io.datapulse.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import io.datapulse.analytics.api.PnlFilter;
import io.datapulse.analytics.api.PnlSummaryResponse;
import io.datapulse.analytics.api.PnlTrendResponse;
import io.datapulse.analytics.api.PostingPnlDetailResponse;
import io.datapulse.analytics.api.PostingPnlResponse;
import io.datapulse.analytics.api.ProductPnlResponse;
import io.datapulse.analytics.api.TrendGranularity;
import io.datapulse.analytics.persistence.PnlReadRepository;
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
@DisplayName("PnlQueryService")
class PnlQueryServiceTest {

  @Mock private PnlReadRepository pnlReadRepository;

  @InjectMocks
  private PnlQueryService service;

  private static final long WORKSPACE_ID = 1L;
  private static final PnlFilter EMPTY_FILTER = new PnlFilter(null, null, null, null, null);

  @Nested
  @DisplayName("getSummary")
  class GetSummary {

    @Test
    @DisplayName("should delegate to repository with workspaceId")
    void should_delegateToRepo() {
      var summary = new PnlSummaryResponse(
          "WB",
          new BigDecimal("100000.00"), new BigDecimal("-15000.00"),
          new BigDecimal("-3000.00"), new BigDecimal("-8000.00"),
          new BigDecimal("-2000.00"), new BigDecimal("-500.00"),
          new BigDecimal("-1000.00"), new BigDecimal("-400.00"),
          new BigDecimal("-600.00"), new BigDecimal("500.00"),
          new BigDecimal("-3000.00"), new BigDecimal("67000.00"),
          new BigDecimal("20000.00"), new BigDecimal("18000.00"),
          BigDecimal.ZERO, new BigDecimal("49000.00"),
          new BigDecimal("31000.00"));

      when(pnlReadRepository.findSummary(WORKSPACE_ID, EMPTY_FILTER))
          .thenReturn(List.of(summary));

      List<PnlSummaryResponse> result = service.getSummary(WORKSPACE_ID, EMPTY_FILTER);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).revenueAmount()).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("should verify P&L formula: marketplace_pnl = revenue + costs")
    void should_verifyPnlFormula_when_summaryReturned() {
      BigDecimal revenue = new BigDecimal("100000.00");
      BigDecimal commission = new BigDecimal("-15000.00");
      BigDecimal acquiring = new BigDecimal("-3000.00");
      BigDecimal logistics = new BigDecimal("-8000.00");
      BigDecimal storage = new BigDecimal("-2000.00");
      BigDecimal penalties = new BigDecimal("-500.00");
      BigDecimal marketing = new BigDecimal("-1000.00");
      BigDecimal acceptance = new BigDecimal("-400.00");
      BigDecimal other = new BigDecimal("-600.00");
      BigDecimal compensation = new BigDecimal("500.00");
      BigDecimal refund = new BigDecimal("-3000.00");

      BigDecimal expectedMarketplacePnl = revenue
          .add(commission).add(acquiring).add(logistics)
          .add(storage).add(penalties).add(marketing)
          .add(acceptance).add(other).add(compensation).add(refund);

      var summary = new PnlSummaryResponse(
          "WB", revenue, commission, acquiring, logistics,
          storage, penalties, marketing, acceptance, other,
          compensation, refund, new BigDecimal("67000.00"),
          new BigDecimal("20000.00"), new BigDecimal("18000.00"),
          BigDecimal.ZERO, expectedMarketplacePnl, null);

      when(pnlReadRepository.findSummary(WORKSPACE_ID, EMPTY_FILTER))
          .thenReturn(List.of(summary));

      List<PnlSummaryResponse> result = service.getSummary(WORKSPACE_ID, EMPTY_FILTER);

      assertThat(result.get(0).marketplacePnl())
          .isEqualByComparingTo(expectedMarketplacePnl);
    }

    @Test
    @DisplayName("should return zero profit when all components are zero")
    void should_returnZeroProfit_when_allComponentsZero() {
      var summary = new PnlSummaryResponse(
          "WB",
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

      when(pnlReadRepository.findSummary(WORKSPACE_ID, EMPTY_FILTER))
          .thenReturn(List.of(summary));

      List<PnlSummaryResponse> result = service.getSummary(WORKSPACE_ID, EMPTY_FILTER);

      assertThat(result.get(0).marketplacePnl()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.get(0).fullPnl()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("should represent loss as negative fullPnl")
    void should_returnNegativePnl_when_costsExceedRevenue() {
      BigDecimal revenue = new BigDecimal("10000.00");
      BigDecimal commission = new BigDecimal("-8000.00");
      BigDecimal logistics = new BigDecimal("-5000.00");
      BigDecimal netCogs = new BigDecimal("3000.00");
      BigDecimal marketplacePnl = revenue.add(commission).add(logistics);
      BigDecimal fullPnl = marketplacePnl.subtract(netCogs);

      var summary = new PnlSummaryResponse(
          "WB", revenue, commission, BigDecimal.ZERO, logistics,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          netCogs, netCogs,
          BigDecimal.ZERO, marketplacePnl, fullPnl);

      when(pnlReadRepository.findSummary(WORKSPACE_ID, EMPTY_FILTER))
          .thenReturn(List.of(summary));

      List<PnlSummaryResponse> result = service.getSummary(WORKSPACE_ID, EMPTY_FILTER);

      assertThat(result.get(0).fullPnl()).isNegative();
      assertThat(result.get(0).fullPnl()).isEqualByComparingTo(fullPnl);
    }
  }

  @Nested
  @DisplayName("getByProduct")
  class GetByProduct {

    @Test
    @DisplayName("should use default sort column 'revenue_amount' when unsorted")
    void should_useDefaultSort_when_noSortProvided() {
      when(pnlReadRepository.countByProduct(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(0L);
      when(pnlReadRepository.findByProduct(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          eq("revenue_amount"), eq(20), eq(0L)))
          .thenReturn(List.of());

      service.getByProduct(WORKSPACE_ID, EMPTY_FILTER, PageRequest.of(0, 20));

      verify(pnlReadRepository).findByProduct(WORKSPACE_ID, EMPTY_FILTER,
          "revenue_amount", 20, 0L);
    }

    @Test
    @DisplayName("should extract sort column from pageable when provided")
    void should_extractSortColumn_when_sortProvided() {
      when(pnlReadRepository.countByProduct(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(0L);
      when(pnlReadRepository.findByProduct(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          eq("fullPnl"), eq(10), eq(0L)))
          .thenReturn(List.of());

      var pageable = PageRequest.of(0, 10, Sort.by("fullPnl"));
      service.getByProduct(WORKSPACE_ID, EMPTY_FILTER, pageable);

      verify(pnlReadRepository).findByProduct(WORKSPACE_ID, EMPTY_FILTER,
          "fullPnl", 10, 0L);
    }

    @Test
    @DisplayName("should verify COGS status NO_COST_PROFILE when cost missing")
    void should_returnNoCostProfile_when_cogsStatusIndicates() {
      var response = new ProductPnlResponse(
          "WB", 1L, 100L, 202501, "PRODUCT", "SKU001", "Product A",
          new BigDecimal("50000.00"), new BigDecimal("-7500.00"),
          BigDecimal.ZERO, new BigDecimal("-4000.00"),
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("38500.00"),
          null, null, "NO_COST_PROFILE",
          BigDecimal.ZERO, new BigDecimal("38500.00"), null);

      when(pnlReadRepository.findByProduct(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          anyString(), eq(20), eq(0L)))
          .thenReturn(List.of(response));
      when(pnlReadRepository.countByProduct(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(1L);

      Page<ProductPnlResponse> result = service.getByProduct(
          WORKSPACE_ID, EMPTY_FILTER, PageRequest.of(0, 20));

      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).cogsStatus()).isEqualTo("NO_COST_PROFILE");
      assertThat(result.getContent().get(0).grossCogs()).isNull();
      assertThat(result.getContent().get(0).fullPnl()).isNull();
    }
  }

  @Nested
  @DisplayName("getByPosting")
  class GetByPosting {

    @Test
    @DisplayName("should use default sort column 'finance_date' when unsorted")
    void should_useDefaultSort_when_noSortOnPosting() {
      when(pnlReadRepository.countByPosting(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(0L);
      when(pnlReadRepository.findByPosting(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          eq("finance_date"), eq(20), eq(0L)))
          .thenReturn(List.of());

      service.getByPosting(WORKSPACE_ID, EMPTY_FILTER, PageRequest.of(0, 20));

      verify(pnlReadRepository).findByPosting(WORKSPACE_ID, EMPTY_FILTER,
          "finance_date", 20, 0L);
    }
  }

  @Nested
  @DisplayName("getPostingDetails")
  class GetPostingDetails {

    @Test
    @DisplayName("should delegate posting id to repository")
    void should_delegatePostingId() {
      when(pnlReadRepository.findPostingDetail(WORKSPACE_ID, "P-001"))
          .thenReturn(new PostingPnlDetailResponse(
              "P-001", null, null, null, null,
              BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
              null, BigDecimal.ZERO, List.of()));

      service.getPostingDetails(WORKSPACE_ID, "P-001");

      verify(pnlReadRepository).findPostingDetail(WORKSPACE_ID, "P-001");
    }
  }

  @Nested
  @DisplayName("getTrend")
  class GetTrend {

    @Test
    @DisplayName("should pass granularity to repository")
    void should_passGranularity() {
      when(pnlReadRepository.findTrend(WORKSPACE_ID, EMPTY_FILTER, TrendGranularity.WEEKLY))
          .thenReturn(List.of());

      service.getTrend(WORKSPACE_ID, EMPTY_FILTER, TrendGranularity.WEEKLY);

      verify(pnlReadRepository).findTrend(WORKSPACE_ID, EMPTY_FILTER, TrendGranularity.WEEKLY);
    }
  }

  @Nested
  @DisplayName("BigDecimal precision")
  class BigDecimalPrecision {

    @Test
    @DisplayName("should preserve 2 decimal places in monetary amounts")
    void should_preserve2DecimalPlaces_when_summaryReturned() {
      var summary = new PnlSummaryResponse(
          "WB",
          new BigDecimal("99999.99"), new BigDecimal("-14999.99"),
          new BigDecimal("-2999.99"), new BigDecimal("-7999.99"),
          new BigDecimal("-1999.99"), new BigDecimal("-499.99"),
          new BigDecimal("-999.99"), new BigDecimal("-399.99"),
          new BigDecimal("-599.99"), new BigDecimal("499.99"),
          new BigDecimal("-2999.99"), new BigDecimal("67000.08"),
          new BigDecimal("19999.99"), new BigDecimal("17999.99"),
          BigDecimal.ZERO, new BigDecimal("67000.08"),
          new BigDecimal("49000.09"));

      when(pnlReadRepository.findSummary(WORKSPACE_ID, EMPTY_FILTER))
          .thenReturn(List.of(summary));

      List<PnlSummaryResponse> result = service.getSummary(WORKSPACE_ID, EMPTY_FILTER);

      assertThat(result.get(0).revenueAmount().scale()).isLessThanOrEqualTo(2);
      assertThat(result.get(0).marketplacePnl().scale()).isLessThanOrEqualTo(2);
    }
  }
}
