package io.datapulse.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.datapulse.analytics.api.InventoryFilter;
import io.datapulse.analytics.api.InventoryOverviewResponse;
import io.datapulse.analytics.api.ProductInventoryResponse;
import io.datapulse.analytics.api.StockHistoryResponse;
import io.datapulse.analytics.persistence.InventoryReadRepository;
import io.datapulse.analytics.persistence.WorkspaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryAnalysisService {

  private final InventoryReadRepository inventoryReadRepository;
  private final WorkspaceConnectionRepository connectionRepository;

  public InventoryOverviewResponse getOverview(long workspaceId, InventoryFilter filter) {
    List<Long> connectionIds = resolveConnectionIds(workspaceId);
    if (connectionIds.isEmpty()) {
      return new InventoryOverviewResponse(
          0, 0, 0, 0, BigDecimal.ZERO, List.of());
    }
    InventoryOverviewResponse overview =
        inventoryReadRepository.findOverview(connectionIds, filter);
    List<ProductInventoryResponse> topCritical =
        inventoryReadRepository.findTopCritical(connectionIds);
    return new InventoryOverviewResponse(
        overview.totalSkus(),
        overview.criticalCount(),
        overview.warningCount(),
        overview.normalCount(),
        overview.frozenCapital(),
        topCritical
    );
  }

  public Page<ProductInventoryResponse> getByProduct(
      long workspaceId, InventoryFilter filter, Pageable pageable) {
    List<Long> connectionIds = resolveConnectionIds(workspaceId);
    if (connectionIds.isEmpty()) {
      return Page.empty(pageable);
    }

    String sortColumn = "daysOfCover";
    String sortDirection = "ASC";
    if (pageable.getSort().isSorted()) {
      Sort.Order order = pageable.getSort().iterator().next();
      sortColumn = order.getProperty();
      sortDirection = order.getDirection().name();
    }

    List<ProductInventoryResponse> content = inventoryReadRepository.findByProduct(
        connectionIds, filter, sortColumn, sortDirection,
        pageable.getPageSize(), pageable.getOffset());
    long total = inventoryReadRepository.countByProduct(connectionIds, filter);

    return new PageImpl<>(content, pageable, total);
  }

  public List<StockHistoryResponse> getStockHistory(
      long workspaceId, long productId, LocalDate from, LocalDate to) {
    List<Long> connectionIds = resolveConnectionIds(workspaceId);
    if (connectionIds.isEmpty()) {
      return List.of();
    }
    return inventoryReadRepository.findStockHistory(connectionIds, productId, from, to);
  }

  private List<Long> resolveConnectionIds(long workspaceId) {
    return connectionRepository.findConnectionIdsByWorkspaceId(workspaceId);
  }
}
