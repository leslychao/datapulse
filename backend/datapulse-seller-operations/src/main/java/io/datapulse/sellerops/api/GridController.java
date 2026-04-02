package io.datapulse.sellerops.api;

import io.datapulse.sellerops.config.GridProperties;
import io.datapulse.sellerops.domain.GridExportService;
import io.datapulse.sellerops.domain.GridService;
import io.datapulse.sellerops.domain.SavedViewService;
import io.datapulse.sellerops.persistence.SavedViewEntity;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/grid",
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class GridController {

    private final GridService gridService;
    private final GridExportService exportService;
    private final SavedViewService savedViewService;
    private final GridProperties gridProperties;

    @GetMapping("/kpi")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public GridKpiResponse getKpi(@PathVariable("workspaceId") Long workspaceId) {
        return gridService.getGridKpi(workspaceId);
    }

    @GetMapping
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public Page<GridRowResponse> getGrid(
            @PathVariable("workspaceId") Long workspaceId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "direction", defaultValue = "ASC") String direction,
            @RequestParam(value = "marketplace_type", required = false) List<String> marketplaceType,
            @RequestParam(value = "connection_id", required = false) List<Long> connectionId,
            @RequestParam(value = "status", required = false) List<String> status,
            @RequestParam(value = "sku_code", required = false) String skuCode,
            @RequestParam(value = "product_name", required = false) String productName,
            @RequestParam(value = "category_id", required = false) List<Long> categoryId,
            @RequestParam(value = "margin_min", required = false) BigDecimal marginMin,
            @RequestParam(value = "margin_max", required = false) BigDecimal marginMax,
            @RequestParam(value = "has_manual_lock", required = false) Boolean hasManualLock,
            @RequestParam(value = "has_active_promo", required = false) Boolean hasActivePromo,
            @RequestParam(value = "last_decision", required = false) String lastDecision,
            @RequestParam(value = "last_action_status", required = false) String lastActionStatus,
            @RequestParam(value = "stock_risk", required = false) String stockRisk,
            @RequestParam(value = "view_id", required = false) Long viewId) {

        int clampedSize = Math.min(size, gridProperties.getMaxPageSize());

        GridFilter filter;
        Sort pageSort;

        if (viewId != null) {
            SavedViewEntity view = savedViewService.findById(workspaceId, viewId);
            filter = buildFilterFromView(view);
            pageSort = buildSortFromView(view);
        } else {
            filter = new GridFilter(
                    marketplaceType, connectionId, status, skuCode, productName,
                    categoryId, marginMin, marginMax, hasManualLock, hasActivePromo,
                    lastDecision, lastActionStatus, null, stockRisk
            );
            pageSort = buildSort(sort, direction);
        }

        return gridService.getGridPage(workspaceId, filter,
                PageRequest.of(page, clampedSize, pageSort));
    }

    @GetMapping("/export")
    @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
    public void exportCsv(
            @PathVariable("workspaceId") Long workspaceId,
            @RequestParam(value = "marketplace_type", required = false) List<String> marketplaceType,
            @RequestParam(value = "connection_id", required = false) List<Long> connectionId,
            @RequestParam(value = "status", required = false) List<String> status,
            @RequestParam(value = "sku_code", required = false) String skuCode,
            @RequestParam(value = "product_name", required = false) String productName,
            @RequestParam(value = "category_id", required = false) List<Long> categoryId,
            @RequestParam(value = "margin_min", required = false) BigDecimal marginMin,
            @RequestParam(value = "margin_max", required = false) BigDecimal marginMax,
            @RequestParam(value = "has_manual_lock", required = false) Boolean hasManualLock,
            @RequestParam(value = "has_active_promo", required = false) Boolean hasActivePromo,
            HttpServletResponse response) throws IOException {

        GridFilter filter = new GridFilter(
                marketplaceType, connectionId, status, skuCode, productName,
                categoryId, marginMin, marginMax, hasManualLock, hasActivePromo,
                null, null, null, null
        );

        String filename = "datapulse-export-"
                + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".csv";

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        exportService.exportCsv(workspaceId, filter, response.getOutputStream());
    }

    private Sort buildSort(String column, String direction) {
        if (column == null || !gridService.isSortableColumn(column)) {
            return Sort.unsorted();
        }
        Sort.Direction dir = "DESC".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, column);
    }

    private GridFilter buildFilterFromView(SavedViewEntity view) {
        Map<String, Object> f = view.getFilters();
        return new GridFilter(
                safeStringList(f.get("marketplace_type")),
                safeLongList(f.get("connection_id")),
                safeStringList(f.get("status")),
                f.get("sku_code") instanceof String s ? s : null,
                f.get("product_name") instanceof String s ? s : null,
                safeLongList(f.get("category_id")),
                toBigDecimal(f.get("margin_min")),
                toBigDecimal(f.get("margin_max")),
                f.get("has_manual_lock") instanceof Boolean b ? b : null,
                f.get("has_active_promo") instanceof Boolean b ? b : null,
                f.get("last_decision") instanceof String s ? s : null,
                f.get("last_action_status") instanceof String s ? s : null,
                null
        );
    }

    private Sort buildSortFromView(SavedViewEntity view) {
        if (view.getSortColumn() == null) {
            return Sort.unsorted();
        }
        Sort.Direction dir = "DESC".equalsIgnoreCase(view.getSortDirection())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, view.getSortColumn());
    }

    private List<String> safeStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return null;
    }

    private List<Long> safeLongList(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(v -> ((Number) v).longValue())
                    .toList();
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        return null;
    }
}
