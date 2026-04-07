package io.datapulse.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
import io.datapulse.analytics.persistence.WorkspaceConnectionRepository;
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
  @Mock private WorkspaceConnectionRepository connectionRepository;

  @InjectMocks
  private InventoryAnalysisService service;

  private static final long WORKSPACE_ID = 1L;
  private static final InventoryFilter EMPTY_FILTER = new InventoryFilter(null, null, null);

  @Nested
  @DisplayName("getOverview")
  class GetOverview {

    @Test
    @DisplayName("should return zero overview when no connections")
    void should_returnZeroOverview_when_noConnections() {
      when(connectionRepository.findConnectionIdsByWorkspaceId(WORKSPACE_ID))
          .thenReturn(List.of());

      InventoryOverviewResponse result = service.getOverview(WORKSPACE_ID, EMPTY_FILTER);

      assertThat(result.totalSkus()).isZero();
      assertThat(result.criticalCount()).isZero();
      assertThat(result.warningCount()).isZero();
      assertThat(result.normalCount()).isZero();
      assertThat(result.frozenCapital()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.topCritical()).isEmpty();
    }

    @Test
    @DisplayName("should delegate to repository when connections exist")
    void should_delegateToRepo_when_connectionsExist() {
      List<Long> connIds = List.of(10L);
      when(connectionRepository.findConnectionIdsByWorkspaceId(WORKSPACE_ID))
          .thenReturn(connIds);

      var overview = new InventoryOverviewResponse(
          150, 5, 20, 125, new BigDecimal("450000.00"), List.of());
      when(inventoryReadRepository.findOverview(connIds, EMPTY_FILTER))
          .thenReturn(overview);

      var criticalProduct = new ProductInventoryResponse(
          10L, "WB", 100L, 1L, "SKU001", "Critical Product",
          1, "Moscow", LocalDate.of(2025, 1, 15),
          0, 0, new BigDecimal("35.71"), new BigDecimal("0.0"),
          "CRITICAL", new BigDecimal("250.00"), null, null);
      when(inventoryReadRepository.findTopCritical(connIds))
          .thenReturn(List.of(criticalProduct));

      InventoryOverviewResponse result = service.getOverview(WORKSPACE_ID, EMPTY_FILTER);

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
    @DisplayName("should return empty page when no connections")
    void should_returnEmptyPage_when_noConnections() {
      when(connectionRepository.findConnectionIdsByWorkspaceId(WORKSPACE_ID))
          .thenReturn(List.of());

      Page<ProductInventoryResponse> result = service.getByProduct(
          WORKSPACE_ID, EMPTY_FILTER, PageRequest.of(0, 20));

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should use 'daysOfCover' ASC as default sort")
    void should_useDefaultSort_when_noSortProvided() {
      List<Long> connIds = List.of(10L);
      when(connectionRepository.findConnectionIdsByWorkspaceId(WORKSPACE_ID))
          .thenReturn(connIds);
      when(inventoryReadRepository.countByProduct(connIds, EMPTY_FILTER)).thenReturn(0L);
      when(inventoryReadRepository.findByProduct(eq(connIds), eq(EMPTY_FILTER),
          eq("daysOfCover"), eq("ASC"), eq(20), eq(0L)))
          .thenReturn(List.of());

      service.getByProduct(WORKSPACE_ID, EMPTY_FILTER, PageRequest.of(0, 20));

      verify(inventoryReadRepository).findByProduct(
          connIds, EMPTY_FILTER, "daysOfCover", "ASC", 20, 0L);
    }

    @Test
    @DisplayName("should extract custom sort column and direction from pageable")
    void should_extractSort_when_sortProvided() {
      List<Long> connIds = List.of(10L);
      when(connectionRepository.findConnectionIdsByWorkspaceId(WORKSPACE_ID))
          .thenReturn(connIds);
      when(inventoryReadRepository.countByProduct(connIds, EMPTY_FILTER)).thenReturn(0L);
      when(inventoryReadRepository.findByProduct(eq(connIds), eq(EMPTY_FILTER),
          eq("available"), eq("DESC"), eq(10), eq(0L)))
          .thenReturn(List.of());

      service.getByProduct(WORKSPACE_ID, EMPTY_FILTER,
          PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "available")));

      verify(inventoryReadRepository).findByProduct(
          connIds, EMPTY_FILTER, "available", "DESC", 10, 0L);
    }

    @Test
    @DisplayName("should return page with products when data exists")
    void should_returnPage_when_dataExists() {
      List<Long> connIds = List.of(10L);
      when(connectionRepository.findConnectionIdsByWorkspaceId(WORKSPACE_ID))
          .thenReturn(connIds);

      var product = new ProductInventoryResponse(
          10L, "WB", 100L, 1L, "SKU001", "Product A",
          1, "Moscow", LocalDate.of(2025, 1, 15),
          500, 50,
          new BigDecimal("35.71"),
          new BigDecimal("14.0"),
          "CRITICAL",
          new BigDecimal("250.00"),
          new BigDecimal("125000.00"),
          200);

      when(inventoryReadRepository.findByProduct(eq(connIds), eq(EMPTY_FILTER),
          anyString(), anyString(), eq(20), eq(0L)))
          .thenReturn(List.of(product));
      when(inventoryReadRepository.countByProduct(connIds, EMPTY_FILTER)).thenReturn(1L);

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
    @DisplayName("should return empty list when no connections")
    void should_returnEmpty_when_noConnections() {
      when(connectionRepository.findConnectionIdsByWorkspaceId(WORKSPACE_ID))
          .thenReturn(List.of());

      List<StockHistoryResponse> result = service.getStockHistory(
          WORKSPACE_ID, 100L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should delegate date range to repository")
    void should_delegateDateRange_when_connectionsExist() {
      List<Long> connIds = List.of(10L);
      when(connectionRepository.findConnectionIdsByWorkspaceId(WORKSPACE_ID))
          .thenReturn(connIds);

      LocalDate from = LocalDate.of(2025, 1, 1);
      LocalDate to = LocalDate.of(2025, 1, 31);

      var history = new StockHistoryResponse(
          LocalDate.of(2025, 1, 15), 450, 30, 1, "Moscow");
      when(inventoryReadRepository.findStockHistory(connIds, 100L, from, to))
          .thenReturn(List.of(history));

      List<StockHistoryResponse> result = service.getStockHistory(WORKSPACE_ID, 100L, from, to);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).available()).isEqualTo(450);
      verify(inventoryReadRepository).findStockHistory(connIds, 100L, from, to);
    }
  }
}
