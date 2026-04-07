package io.datapulse.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.datapulse.analytics.api.InventoryFilter;
import io.datapulse.analytics.api.InventoryOverviewResponse;
import io.datapulse.analytics.api.ProductInventoryResponse;
import io.datapulse.analytics.api.StockHistoryResponse;
import io.datapulse.analytics.persistence.InventoryReadRepository;
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

  public InventoryOverviewResponse getOverview(long workspaceId, InventoryFilter filter) {
    InventoryOverviewResponse overview =
        inventoryReadRepository.findOverview(workspaceId, filter);
    List<ProductInventoryResponse> topCritical =
        inventoryReadRepository.findTopCritical(workspaceId);
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

    String sortColumn = "daysOfCover";
    String sortDirection = "ASC";
    if (pageable.getSort().isSorted()) {
      Sort.Order order = pageable.getSort().iterator().next();
      sortColumn = order.getProperty();
      sortDirection = order.getDirection().name();
    }

    List<ProductInventoryResponse> content = inventoryReadRepository.findByProduct(
        workspaceId, filter, sortColumn, sortDirection,
        pageable.getPageSize(), pageable.getOffset());
    long total = inventoryReadRepository.countByProduct(workspaceId, filter);

    return new PageImpl<>(content, pageable, total);
  }

  public List<StockHistoryResponse> getStockHistory(
      long workspaceId, long productId, LocalDate from, LocalDate to) {
    return inventoryReadRepository.findStockHistory(workspaceId, productId, from, to);
  }
}
