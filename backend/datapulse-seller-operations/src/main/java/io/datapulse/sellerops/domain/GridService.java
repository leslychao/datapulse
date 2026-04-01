package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.api.GridFilter;
import io.datapulse.sellerops.api.GridKpiResponse;
import io.datapulse.sellerops.api.GridRowResponse;
import io.datapulse.sellerops.config.GridProperties;
import io.datapulse.sellerops.persistence.ClickHouseEnrichment;
import io.datapulse.sellerops.persistence.ClickHouseKpiRow;
import io.datapulse.sellerops.persistence.GridClickHouseReadRepository;
import io.datapulse.sellerops.persistence.GridKpiRow;
import io.datapulse.sellerops.persistence.GridPostgresReadRepository;
import io.datapulse.sellerops.persistence.GridRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GridService {

    private final GridPostgresReadRepository pgRepository;
    private final GridClickHouseReadRepository chRepository;
    private final GridProperties gridProperties;

    @Transactional(readOnly = true)
    public Page<GridRowResponse> getGridPage(long workspaceId, GridFilter filter,
                                              Pageable pageable) {
        Page<GridRow> pgPage = pgRepository.findAll(workspaceId, filter, pageable);
        if (pgPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> offerIds = pgPage.getContent().stream()
                .map(GridRow::getOfferId)
                .toList();

        Map<Long, ClickHouseEnrichment> enrichment = fetchEnrichmentSafely(offerIds);

        List<GridRowResponse> enrichedRows = pgPage.getContent().stream()
                .map(row -> toGridRowResponse(row, enrichment.get(row.getOfferId())))
                .toList();

        return new PageImpl<>(enrichedRows, pageable, pgPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public GridKpiResponse getGridKpi(long workspaceId) {
        GridKpiRow pgKpi = pgRepository.findKpi(workspaceId);
        ClickHouseKpiRow chKpi = fetchChKpiSafely(workspaceId);

        return new GridKpiResponse(
                pgKpi.totalOffers(),
                pgKpi.avgMarginPct(),
                null,
                pgKpi.pendingActionsCount(),
                chKpi.criticalStockCount(),
                chKpi.revenue30dTotal(),
                chKpi.revenue30dTrend());
    }

    public boolean isSortableColumn(String column) {
        return pgRepository.isSortableColumn(column);
    }

    private ClickHouseKpiRow fetchChKpiSafely(long workspaceId) {
        try {
            List<Long> connectionIds = pgRepository.findConnectionIds(workspaceId);
            return chRepository.findKpi(connectionIds);
        } catch (Exception e) {
            log.warn("ClickHouse KPI query failed, returning zeroes: error={}", e.getMessage());
            return new ClickHouseKpiRow(0, null, null);
        }
    }

    private Map<Long, ClickHouseEnrichment> fetchEnrichmentSafely(List<Long> offerIds) {
        try {
            return chRepository.findEnrichment(offerIds);
        } catch (Exception e) {
            log.warn("ClickHouse enrichment failed, returning degraded grid: error={}",
                    e.getMessage());
            return Map.of();
        }
    }

    private GridRowResponse toGridRowResponse(GridRow row, ClickHouseEnrichment ch) {
        return new GridRowResponse(
                row.getOfferId(),
                row.getSkuCode(),
                row.getProductName(),
                row.getMarketplaceType(),
                row.getConnectionName(),
                row.getStatus(),
                row.getCategory(),
                row.getCurrentPrice(),
                row.getDiscountPrice(),
                row.getCostPrice(),
                row.getMarginPct(),
                row.getAvailableStock(),
                ch != null ? ch.getDaysOfCover() : null,
                ch != null ? ch.getStockRisk() : null,
                ch != null ? ch.getRevenue30d() : null,
                ch != null ? ch.getNetPnl30d() : null,
                ch != null ? ch.getVelocity14d() : null,
                ch != null ? ch.getReturnRatePct() : null,
                row.getActivePolicy(),
                row.getLastDecision(),
                row.getLastActionStatus(),
                row.getPromoStatus(),
                row.isManualLock(),
                row.getSimulatedPrice(),
                row.getSimulatedDeltaPct(),
                row.getLastSyncAt(),
                computeFreshness(row.getLastSyncAt())
        );
    }

    private String computeFreshness(OffsetDateTime lastSyncAt) {
        if (lastSyncAt == null) {
            return DataFreshness.STALE.name();
        }
        long hoursSinceSync = Duration.between(lastSyncAt, OffsetDateTime.now()).toHours();
        return hoursSinceSync > gridProperties.getFreshnessThresholdHours()
                ? DataFreshness.STALE.name()
                : DataFreshness.FRESH.name();
    }
}
