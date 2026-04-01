package io.datapulse.sellerops.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.sellerops.api.GridFilter;
import io.datapulse.sellerops.api.GridRowResponse;
import io.datapulse.sellerops.api.OfferDetailResponse;
import io.datapulse.sellerops.config.GridProperties;
import io.datapulse.sellerops.persistence.ClickHouseEnrichment;
import io.datapulse.sellerops.persistence.GridClickHouseReadRepository;
import io.datapulse.sellerops.persistence.GridPostgresReadRepository;
import io.datapulse.sellerops.persistence.GridRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public OfferDetailResponse getOfferDetail(long workspaceId, long offerId) {
        GridRow row = pgRepository.findOfferById(workspaceId, offerId);
        if (row == null) {
            throw NotFoundException.entity("marketplace_offer", offerId);
        }

        Map<Long, ClickHouseEnrichment> enrichment =
                fetchEnrichmentSafely(List.of(offerId));
        ClickHouseEnrichment ch = enrichment.get(offerId);

        return toOfferDetailResponse(row, ch);
    }

    public boolean isSortableColumn(String column) {
        return pgRepository.isSortableColumn(column);
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

    private OfferDetailResponse toOfferDetailResponse(GridRow row, ClickHouseEnrichment ch) {
        return new OfferDetailResponse(
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
                null, null, null, null, null,
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
        long hoursSinceSync = java.time.Duration.between(lastSyncAt, OffsetDateTime.now()).toHours();
        return hoursSinceSync > gridProperties.getFreshnessThresholdHours()
                ? DataFreshness.STALE.name()
                : DataFreshness.FRESH.name();
    }
}
