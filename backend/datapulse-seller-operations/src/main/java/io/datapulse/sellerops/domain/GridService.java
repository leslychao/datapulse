package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.api.GridKpiResponse;
import io.datapulse.sellerops.api.GridRowResponse;
import io.datapulse.sellerops.api.MatchingIdsResponse;
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
import org.springframework.data.domain.PageRequest;
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

    public record GridPageResult(Page<GridRowResponse> page, boolean chSortFallback) {}

    @Transactional(readOnly = true)
    public GridPageResult getGridPage(long workspaceId, GridFilter filter,
                                      Pageable pageable) {
        GridFilter resolvedFilter = resolveChPreFilter(workspaceId, filter);

        String sortColumn = extractSortColumn(pageable);
        if (sortColumn != null && chRepository.isChSortColumn(sortColumn)) {
            return getGridPageWithChSort(workspaceId, resolvedFilter, pageable, sortColumn);
        }

        Page<GridRow> pgPage = pgRepository.findAll(workspaceId, resolvedFilter, pageable);
        if (pgPage.isEmpty()) {
            return new GridPageResult(Page.empty(pageable), false);
        }

        List<Long> offerIds = pgPage.getContent().stream()
                .map(GridRow::getOfferId)
                .toList();

        Map<Long, ClickHouseEnrichment> enrichment = fetchEnrichmentSafely(offerIds);

        List<GridRowResponse> enrichedRows = pgPage.getContent().stream()
                .map(row -> toGridRowResponse(row, enrichment.get(row.getOfferId())))
                .toList();

        return new GridPageResult(
                new PageImpl<>(enrichedRows, pageable, pgPage.getTotalElements()), false);
    }

    private GridPageResult getGridPageWithChSort(long workspaceId, GridFilter filter,
                                                  Pageable pageable, String sortColumn) {
        String direction = pageable.getSort().stream()
                .findFirst()
                .map(o -> o.getDirection().name())
                .orElse("ASC");

        List<Long> connectionIds = pgRepository.findConnectionIds(workspaceId);
        int maxResults = (int) pageable.getOffset() + pageable.getPageSize();

        List<Long> sortedIds = ChSafeQuery.getOrFallback(
                () -> chRepository.findSortedOfferIds(connectionIds, sortColumn, direction, maxResults),
                null, "sort");
        if (sortedIds == null) {
            GridPageResult fallback = getGridPage(workspaceId, filter,
                    PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
            return new GridPageResult(fallback.page(), true);
        }

        if (sortedIds.isEmpty()) {
            return new GridPageResult(Page.empty(pageable), false);
        }

        int fromIdx = (int) Math.min(pageable.getOffset(), sortedIds.size());
        int toIdx = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), sortedIds.size());
        List<Long> pageIds = sortedIds.subList(fromIdx, toIdx);

        List<GridRow> pgRows = pgRepository.findByOrderedIds(workspaceId, pageIds);
        Map<Long, ClickHouseEnrichment> enrichment = fetchEnrichmentSafely(pageIds);

        List<GridRowResponse> enrichedRows = pgRows.stream()
                .map(row -> toGridRowResponse(row, enrichment.get(row.getOfferId())))
                .toList();

        return new GridPageResult(
                new PageImpl<>(enrichedRows, pageable, sortedIds.size()), false);
    }

    private static final int MATCHING_IDS_LIMIT = 500;

    @Transactional(readOnly = true)
    public MatchingIdsResponse getMatchingOfferIds(long workspaceId, GridFilter filter) {
        GridFilter resolved = resolveChPreFilter(workspaceId, filter);
        List<Long> allIds = pgRepository.findMatchingOfferIds(
                workspaceId, resolved, MATCHING_IDS_LIMIT + 1);
        boolean truncated = allIds.size() > MATCHING_IDS_LIMIT;
        List<Long> resultIds = truncated
                ? allIds.subList(0, MATCHING_IDS_LIMIT)
                : allIds;
        return new MatchingIdsResponse(resultIds, allIds.size(), truncated);
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

    private GridFilter resolveChPreFilter(long workspaceId, GridFilter filter) {
        if (filter == null || filter.stockRisk() == null) {
            return filter;
        }
        List<Long> connectionIds = pgRepository.findConnectionIds(workspaceId);
        List<Long> chOfferIds = ChSafeQuery.getOrFallback(
                () -> chRepository.findOfferIdsByStockRisk(connectionIds, filter.stockRisk()),
                List.of(), "pre-filter/stockRisk");
        if (chOfferIds.isEmpty()) {
            return filter;
        }
        List<Long> merged = filter.connectionId() != null
                ? filter.connectionId() : List.of();
        return new GridFilter(
                filter.marketplaceType(), merged, filter.status(),
                filter.skuCode(), filter.productName(), filter.categoryId(),
                filter.marginMin(), filter.marginMax(), filter.hasManualLock(),
                filter.hasActivePromo(), filter.lastDecision(), filter.lastActionStatus(),
                filter.viewId(), null
        );
    }

    private String extractSortColumn(Pageable pageable) {
        return pageable.getSort().stream()
                .findFirst()
                .map(org.springframework.data.domain.Sort.Order::getProperty)
                .orElse(null);
    }

    private ClickHouseKpiRow fetchChKpiSafely(long workspaceId) {
        List<Long> connectionIds = pgRepository.findConnectionIds(workspaceId);
        return ChSafeQuery.getOrFallback(
                () -> chRepository.findKpi(connectionIds),
                new ClickHouseKpiRow(0, null, null), "kpi");
    }

    private Map<Long, ClickHouseEnrichment> fetchEnrichmentSafely(List<Long> offerIds) {
        return ChSafeQuery.getOrFallback(
                () -> chRepository.findEnrichment(offerIds),
                Map.of(), "enrichment");
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
                ch != null ? ch.getAdSpend30d() : null,
                ch != null ? ch.getDrr30dPct() : null,
                ch != null ? ch.getAdCpo() : null,
                ch != null ? ch.getAdRoas() : null,
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
