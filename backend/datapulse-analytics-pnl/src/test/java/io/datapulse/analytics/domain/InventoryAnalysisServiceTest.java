package io.datapulse.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.datapulse.analytics.api.InventoryFilter;
import io.datapulse.analytics.api.InventoryOverviewResponse;
import io.datapulse.analytics.api.ProductInventoryResponse;
import io.datapulse.analytics.api.StockHistoryResponse;
import io.datapulse.analytics.persistence.InventoryReadRepository;
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
@DisplayName("InventoryAnalysisService")
class InventoryAnalysisServiceTest {

  @Mock private InventoryReadRepository inventoryReadRepository;

  @InjectMocks
  private InventoryAnalysisService service;

  private static final long WORKSPACE_ID = 1L;
  private static final InventoryFilter EMPTY_FILTER = new InventoryFilter(null, null, null);

  @Nested
  @DisplayName("getOverview")
  class GetOverview {

    @Test
    @DisplayName("should return overview from repository")
    void should_returnOverview_when_dataExists() {
      var overview = new InventoryOverviewResponse(
          150, 5, 20, 125, new BigDecimal("450000.00"), List.of());
      when(inventoryReadRepository.findOverview(WORKSPACE_ID))
          .thenReturn(overview);

      var criticalProduct = new ProductInventoryResponse(
          "WB", 100L, 1L, "SKU001", "Critical Product",
          1, "Moscow", LocalDate.of(2025, 1, 15),
          0, 0, new BigDecimal("35.71"), new BigDecimal("0.0"),
          "CRITICAL", new BigDecimal("250.00"), null, null);
      when(inventoryReadRepository.findTopCritical(WORKSPACE_ID))
          .thenReturn(List.of(criticalProduct));

      InventoryOverviewResponse result = service.getOverview(WORKSPACE_ID);

      assertThat(result.totalSkus()).isEqualTo(150);
      assertThat(result.criticalCount()).isEqualTo(5);
      assertThat(result.frozenCapital()).isEqualByComparingTo("450000.00");
      assertThat(result.topCritical()).hasSize(1);
      assertThat(result.topCritical().get(0).skuCode()).isEqualTo("SKU001");
    }
  }

  @Nested
  @DisplayName("getByProduct")
  class GetByProduct {

    @Test
    @DisplayName("should use 'daysOfCover' ASC as default sort")
    void should_useDefaultSort_when_noSortProvided() {
      when(inventoryReadRepository.countByProduct(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(0L);
      when(inventoryReadRepository.findByProduct(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          eq("daysOfCover"), eq("ASC"), eq(20), eq(0L)))
          .thenReturn(List.of());

      service.getByProduct(WORKSPACE_ID, EMPTY_FILTER, PageRequest.of(0, 20));

      verify(inventoryReadRepository).findByProduct(
          WORKSPACE_ID, EMPTY_FILTER, "daysOfCover", "ASC", 20, 0L);
    }

    @Test
    @DisplayName("should extract custom sort column and direction from pageable")
    void should_extractSort_when_sortProvided() {
      when(inventoryReadRepository.countByProduct(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(0L);
      when(inventoryReadRepository.findByProduct(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          eq("available"), eq("DESC"), eq(10), eq(0L)))
          .thenReturn(List.of());

      service.getByProduct(WORKSPACE_ID, EMPTY_FILTER,
          PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "available")));

      verify(inventoryReadRepository).findByProduct(
          WORKSPACE_ID, EMPTY_FILTER, "available", "DESC", 10, 0L);
    }

    @Test
    @DisplayName("should return page with products when data exists")
    void should_returnPage_when_dataExists() {
      var product = new ProductInventoryResponse(
          "WB", 100L, 1L, "SKU001", "Product A",
          1, "Moscow", LocalDate.of(2025, 1, 15),
          500, 50,
          new BigDecimal("35.71"),
          new BigDecimal("14.0"),
          "CRITICAL",
          new BigDecimal("250.00"),
          new BigDecimal("125000.00"),
          200);

      when(inventoryReadRepository.findByProduct(eq(WORKSPACE_ID), eq(EMPTY_FILTER),
          anyString(), anyString(), eq(20), eq(0L)))
          .thenReturn(List.of(product));
      when(inventoryReadRepository.countByProduct(WORKSPACE_ID, EMPTY_FILTER)).thenReturn(1L);

      Page<ProductInventoryResponse> result = service.getByProduct(
          WORKSPACE_ID, EMPTY_FILTER, PageRequest.of(0, 20));

      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).daysOfCover())
          .isEqualByComparingTo("14.0");
      assertThat(result.getContent().get(0).stockOutRisk()).isEqualTo("CRITICAL");
    }
  }

  @Nested
  @DisplayName("getStockHistory")
  class GetStockHistory {

    @Test
    @DisplayName("should delegate per-product query when productId is provided")
    void should_delegatePerProduct_when_productIdProvided() {
      LocalDate from = LocalDate.of(2025, 1, 1);
      LocalDate to = LocalDate.of(2025, 1, 31);

      var history = new StockHistoryResponse(
          LocalDate.of(2025, 1, 15), 450, 30, 1, "Moscow");
      when(inventoryReadRepository.findStockHistory(WORKSPACE_ID, 100L, from, to))
          .thenReturn(List.of(history));

      List<StockHistoryResponse> result = service.getStockHistory(WORKSPACE_ID, 100L, from, to);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).available()).isEqualTo(450);
      verify(inventoryReadRepository).findStockHistory(WORKSPACE_ID, 100L, from, to);
    }

    @Test
    @DisplayName("should delegate aggregate query when productId is null")
    void should_delegateAggregate_when_productIdIsNull() {
      LocalDate from = LocalDate.of(2025, 1, 1);
      LocalDate to = LocalDate.of(2025, 1, 31);

      var aggregated = new StockHistoryResponse(
          LocalDate.of(2025, 1, 15), 12000, 3200, null, null);
      when(inventoryReadRepository.findAggregateStockHistory(WORKSPACE_ID, from, to))
          .thenReturn(List.of(aggregated));

      List<StockHistoryResponse> result = service.getStockHistory(WORKSPACE_ID, null, from, to);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).available()).isEqualTo(12000);
      assertThat(result.get(0).warehouseId()).isNull();
      verify(inventoryReadRepository).findAggregateStockHistory(WORKSPACE_ID, from, to);
    }
  }
}
