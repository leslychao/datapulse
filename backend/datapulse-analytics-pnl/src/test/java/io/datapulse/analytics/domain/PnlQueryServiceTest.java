package io.datapulse.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import io.datapulse.analytics.api.PnlAggregatedSummaryResponse;
import io.datapulse.analytics.api.PnlFilter;
import io.datapulse.analytics.api.PnlTrendResponse;
import io.datapulse.analytics.api.PostingPnlDetailResponse;
import io.datapulse.analytics.api.PostingPnlResponse;
import io.datapulse.analytics.api.ProductPnlResponse;
import io.datapulse.analytics.api.TrendGranularity;
import io.datapulse.analytics.persistence.PnlAggregatedRow;
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
  private static final PnlFilter EMPTY_FILTER =
      new PnlFilter(null, null, null, null, null, null);

  @Nested
  @DisplayName("getAggregatedSummary")
  class GetAggregatedSummary {

    @Test
    @DisplayName("should return empty summary when period is invalid")
    void should_returnEmpty_when_periodInvalid() {
      var filter = new PnlFilter(null, null, "invalid", null, null, null);

      PnlAggregatedSummaryResponse result =
          service.getAggregatedSummary(WORKSPACE_ID, filter);

      assertThat(result.revenueAmount()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.fullPnl()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.costBreakdown()).isEmpty();
    }

    @Test
    @DisplayName("should return empty summary when no data for period")
    void should_returnEmpty_when_noData() {
      var filter = new PnlFilter(null, null, "2025-03", null, null, null);
      when(pnlReadRepository.findAggregatedSummary(WORKSPACE_ID, 202503, filter))
          .thenReturn(null);

      PnlAggregatedSummaryResponse result =
          service.getAggregatedSummary(WORKSPACE_ID, filter);

      assertThat(result.revenueAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("should compute costs as absolute values for display")
    void should_returnAbsoluteCosts_when_costsNegative() {
      var filter = new PnlFilter(null, null, "2025-03", null, null, null);
      var row = buildRow(
          new BigDecimal("100000.00"),
          new BigDecimal("-15000.00"),
          new BigDecimal("-3000.00"),
          new BigDecimal("-8000.00"),
          new BigDecimal("-2000.00"),
          new BigDecimal("-500.00"),
          new BigDecimal("-1000.00"),
          new BigDecimal("-400.00"),
          new BigDecimal("-600.00"),
          new BigDecimal("500.00"),
          new BigDecimal("-3000.00"),
          new BigDecimal("67000.00"),
          new BigDecimal("20000.00"),
          new BigDecimal("18000.00"),
          BigDecimal.ZERO,
          new BigDecimal("49000.00"));

      when(pnlReadRepository.findAggregatedSummary(WORKSPACE_ID, 202503, filter))
          .thenReturn(row);
      when(pnlReadRepository.findAggregatedSummary(WORKSPACE_ID, 202502, filter))
          .thenReturn(null);
      when(pnlReadRepository.findReconciliationResidual(WORKSPACE_ID, 202503, filter))
          .thenReturn(BigDecimal.ZERO);

      PnlAggregatedSummaryResponse result =
          service.getAggregatedSummary(WORKSPACE_ID, filter);

      assertThat(result.totalCostsAmount()).isPositive();
      assertThat(result.costBreakdown()).isNotEmpty();
      result.costBreakdown().forEach(item ->
          assertThat(item.amount()).isPositive());
    }

    @Test
    @DisplayName("should compute reconciliation ratio as |residual| / |net_payout|")
    void should_computeReconciliationRatio() {
      var filter = new PnlFilter(null, null, "2025-03", null, null, null);
      var row = buildRow(
          new BigDecimal("100000.00"),
          new BigDecimal("-15000.00"), BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO,
          new BigDecimal("85000.00"),
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          new BigDecimal("85000.00"));

      when(pnlReadRepository.findAggregatedSummary(WORKSPACE_ID, 202503, filter))
          .thenReturn(row);
      when(pnlReadRepository.findAggregatedSummary(WORKSPACE_ID, 202502, filter))
          .thenReturn(null);
      when(pnlReadRepository.findReconciliationResidual(WORKSPACE_ID, 202503, filter))
          .thenReturn(new BigDecimal("850.00"));

      PnlAggregatedSummaryResponse result =
          service.getAggregatedSummary(WORKSPACE_ID, filter);

      // |850| / |85000| * 100 = 1.0000
      assertThat(result.reconciliationRatio())
          .isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("should return zero reconciliation ratio when net_payout is zero")
    void should_returnZeroRatio_when_netPayoutZero() {
      var filter = new PnlFilter(null, null, "2025-03", null, null, null);
      var row = buildRow(
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO);

      when(pnlReadRepository.findAggregatedSummary(WORKSPACE_ID, 202503, filter))
          .thenReturn(row);
      when(pnlReadRepository.findAggregatedSummary(WORKSPACE_ID, 202502, filter))
          .thenReturn(null);
      when(pnlReadRepository.findReconciliationResidual(WORKSPACE_ID, 202503, filter))
          .thenReturn(BigDecimal.ZERO);

      PnlAggregatedSummaryResponse result =
          service.getAggregatedSummary(WORKSPACE_ID, filter);

      assertThat(result.reconciliationRatio())
          .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("should compute fullPnl = marketplacePnl - advertising - cogs")
    void should_computeFullPnl() {
      var filter = new PnlFilter(null, null, "2025-03", null, null, null);
      var row = buildRow(
          new BigDecimal("100000.00"),
          new BigDecimal("-15000.00"), BigDecimal.ZERO,
          new BigDecimal("-8000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO,
          new BigDecimal("77000.00"),
          new BigDecimal("20000.00"), new BigDecimal("18000.00"),
          new BigDecimal("5000.00"),
          new BigDecimal("77000.00"));

      when(pnlReadRepository.findAggregatedSummary(WORKSPACE_ID, 202503, filter))
          .thenReturn(row);
      when(pnlReadRepository.findAggregatedSummary(WORKSPACE_ID, 202502, filter))
          .thenReturn(null);
      when(pnlReadRepository.findReconciliationResidual(WORKSPACE_ID, 202503, filter))
          .thenReturn(BigDecimal.ZERO);

      PnlAggregatedSummaryResponse result =
          service.getAggregatedSummary(WORKSPACE_ID, filter);

      // 77000 - 5000 - 18000 = 54000
      assertThat(result.fullPnl()).isEqualByComparingTo("54000.00");
    }

    private PnlAggregatedRow buildRow(
        BigDecimal revenue,
        BigDecimal commission, BigDecimal acquiring,
        BigDecimal logistics, BigDecimal storage,
        BigDecimal penalties, BigDecimal marketing,
        BigDecimal acceptance, BigDecimal other,
        BigDecimal compensation, BigDecimal refund,
        BigDecimal netPayout,
        BigDecimal grossCogs, BigDecimal netCogs,
        BigDecimal advertisingCost,
        BigDecimal marketplacePnl) {
      var row = new PnlAggregatedRow();
      row.setRevenueAmount(revenue);
      row.setMarketplaceCommissionAmount(commission);
      row.setAcquiringCommissionAmount(acquiring);
      row.setLogisticsCostAmount(logistics);
      row.setStorageCostAmount(storage);
      row.setPenaltiesAmount(penalties);
      row.setMarketingCostAmount(marketing);
      row.setAcceptanceCostAmount(acceptance);
      row.setOtherMarketplaceChargesAmount(other);
      row.setCompensationAmount(compensation);
      row.setRefundAmount(refund);
      row.setNetPayout(netPayout);
      row.setGrossCogs(grossCogs);
      row.setNetCogs(netCogs);
      row.setAdvertisingCost(advertisingCost);
      row.setMarketplacePnl(marketplacePnl);
      return row;
    }
  }

  @Nested
  @DisplayName("getByProduct")
  class GetByProduct {

    @Test
    @DisplayName("should convert camelCase sort to snake_case and pass direction")
    void should_convertSortColumn_when_sortProvided() {
      when(pnlReadRepository.countByProduct(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(0L);
      when(pnlReadRepository.findByProduct(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          eq("full_pnl"), eq("ASC"), eq(10), eq(0L)))
          .thenReturn(List.of());

      var pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "fullPnl"));
      service.getByProduct(WORKSPACE_ID, EMPTY_FILTER, pageable);

      verify(pnlReadRepository).findByProduct(WORKSPACE_ID, EMPTY_FILTER,
          "full_pnl", "ASC", 10, 0L);
    }

    @Test
    @DisplayName("should use default sort 'revenue_amount DESC' when unsorted")
    void should_useDefaultSort_when_noSortProvided() {
      when(pnlReadRepository.countByProduct(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(0L);
      when(pnlReadRepository.findByProduct(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          eq("revenue_amount"), eq("DESC"), eq(20), eq(0L)))
          .thenReturn(List.of());

      service.getByProduct(WORKSPACE_ID, EMPTY_FILTER, PageRequest.of(0, 20));

      verify(pnlReadRepository).findByProduct(WORKSPACE_ID, EMPTY_FILTER,
          "revenue_amount", "DESC", 20, 0L);
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
          anyString(), anyString(), eq(20), eq(0L)))
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
    @DisplayName("should use default sort 'finance_date DESC' when unsorted")
    void should_useDefaultSort_when_noSortOnPosting() {
      when(pnlReadRepository.countByPosting(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(0L);
      when(pnlReadRepository.findByPosting(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          eq("finance_date"), eq("DESC"), eq(20), eq(0L)))
          .thenReturn(List.of());

      service.getByPosting(WORKSPACE_ID, EMPTY_FILTER, PageRequest.of(0, 20));

      verify(pnlReadRepository).findByPosting(WORKSPACE_ID, EMPTY_FILTER,
          "finance_date", "DESC", 20, 0L);
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
    @DisplayName("should pass granularity to repository with default bounds when no from/to")
    void should_passGranularity() {
      when(pnlReadRepository.findTrend(eq(WORKSPACE_ID), any(PnlFilter.class),
          eq(TrendGranularity.WEEKLY)))
          .thenReturn(List.of());

      service.getTrend(WORKSPACE_ID, EMPTY_FILTER, TrendGranularity.WEEKLY);

      verify(pnlReadRepository).findTrend(eq(WORKSPACE_ID), any(PnlFilter.class),
          eq(TrendGranularity.WEEKLY));
    }

    @Test
    @DisplayName("should preserve explicit from/to without override")
    void should_preserveExplicitBounds() {
      var filter = new PnlFilter(
          java.time.LocalDate.of(2026, 1, 1),
          java.time.LocalDate.of(2026, 3, 31),
          null, null, null, null);

      when(pnlReadRepository.findTrend(WORKSPACE_ID, filter, TrendGranularity.MONTHLY))
          .thenReturn(List.of());

      service.getTrend(WORKSPACE_ID, filter, TrendGranularity.MONTHLY);

      verify(pnlReadRepository).findTrend(WORKSPACE_ID, filter, TrendGranularity.MONTHLY);
    }
  }
}
