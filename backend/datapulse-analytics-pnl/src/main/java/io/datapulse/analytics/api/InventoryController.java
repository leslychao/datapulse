package io.datapulse.analytics.api;

import java.time.LocalDate;
import java.util.List;

import io.datapulse.analytics.domain.InventoryAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/analytics/inventory",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryAnalysisService inventoryAnalysisService;

    @GetMapping("/overview")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public InventoryOverviewResponse getOverview(
            @PathVariable("workspaceId") long workspaceId,
            InventoryFilter filter) {
        return inventoryAnalysisService.getOverview(workspaceId, filter);
    }

    @GetMapping("/by-product")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<ProductInventoryResponse> getByProduct(
            @PathVariable("workspaceId") long workspaceId,
            InventoryFilter filter,
            Pageable pageable) {
        return inventoryAnalysisService.getByProduct(workspaceId, filter, pageable);
    }

    @GetMapping("/stock-history")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public List<StockHistoryResponse> getStockHistory(
            @PathVariable("workspaceId") long workspaceId,
            @RequestParam(required = false) Long productId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return inventoryAnalysisService.getStockHistory(workspaceId, productId, from, to);
    }
}
