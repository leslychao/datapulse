package io.datapulse.analytics.api;

import java.time.LocalDate;
import java.util.List;

import io.datapulse.analytics.domain.InventoryAnalysisService;
import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/analytics/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryAnalysisService inventoryAnalysisService;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/overview")
    public InventoryOverviewResponse getOverview(InventoryFilter filter) {
        return inventoryAnalysisService.getOverview(workspaceContext.getWorkspaceId(), filter);
    }

    @GetMapping("/by-product")
    public Page<ProductInventoryResponse> getByProduct(InventoryFilter filter, Pageable pageable) {
        return inventoryAnalysisService.getByProduct(
                workspaceContext.getWorkspaceId(), filter, pageable);
    }

    @GetMapping("/stock-history")
    public List<StockHistoryResponse> getStockHistory(@RequestParam long productId,
                                                       @RequestParam LocalDate from,
                                                       @RequestParam LocalDate to) {
        return inventoryAnalysisService.getStockHistory(
                workspaceContext.getWorkspaceId(), productId, from, to);
    }
}
