package io.datapulse.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import io.datapulse.analytics.api.PnlAggregatedSummaryResponse;
import io.datapulse.analytics.api.PnlAggregatedSummaryResponse.CostBreakdownItem;
import io.datapulse.analytics.api.PnlFilter;
import io.datapulse.analytics.api.PnlSummaryResponse;
import io.datapulse.analytics.api.PnlTrendResponse;
import io.datapulse.analytics.api.PostingPnlDetailResponse;
import io.datapulse.analytics.api.PostingPnlResponse;
import io.datapulse.analytics.api.ProductPnlResponse;
import io.datapulse.analytics.api.TrendGranularity;
import io.datapulse.analytics.persistence.PnlAggregatedRow;
import io.datapulse.analytics.persistence.PnlReadRepository;
import io.datapulse.analytics.persistence.WorkspaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PnlQueryService {

    private final PnlReadRepository pnlReadRepository;
    private final WorkspaceConnectionRepository connectionRepository;

    public PnlAggregatedSummaryResponse getAggregatedSummary(long workspaceId, PnlFilter filter) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (filter.connectionId() != null) {
            connectionIds = connectionIds.stream()
                .filter(id -> id.equals(filter.connectionId()))
                .toList();
        }
        if (connectionIds.isEmpty()) {
            return emptyAggregatedSummary();
        }

        int currentPeriod = parsePeriod(filter.period());
        if (currentPeriod == 0) {
            return emptyAggregatedSummary();
        }

        PnlAggregatedRow current = pnlReadRepository.findAggregatedSummary(
            connectionIds, currentPeriod);
        if (current == null) {
            return emptyAggregatedSummary();
        }

        int prevPeriod = previousPeriod(currentPeriod);
        PnlAggregatedRow prev = pnlReadRepository.findAggregatedSummary(
            connectionIds, prevPeriod);

        BigDecimal totalCosts = sumCosts(current);
        BigDecimal prevTotalCosts = prev != null ? sumCosts(prev) : null;

        BigDecimal residual = pnlReadRepository.findReconciliationResidual(
            connectionIds, currentPeriod);
        BigDecimal revenue = orZero(current.getRevenueAmount());
        BigDecimal reconciliationRatio = revenue.compareTo(BigDecimal.ZERO) != 0
            ? orZero(residual).divide(revenue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        return new PnlAggregatedSummaryResponse(
            revenue,
            totalCosts,
            orZero(current.getNetCogs()),
            orZero(current.getAdvertisingCost()),
            orZero(current.getMarketplacePnl()),
            orZero(current.getFullPnl()),
            orZero(residual),
            reconciliationRatio,
            deltaPct(current.getRevenueAmount(), prev != null ? prev.getRevenueAmount() : null),
            deltaPct(totalCosts, prevTotalCosts),
            deltaPct(current.getNetCogs(), prev != null ? prev.getNetCogs() : null),
            deltaPct(current.getAdvertisingCost(), prev != null ? prev.getAdvertisingCost() : null),
            deltaPct(current.getFullPnl(), prev != null ? prev.getFullPnl() : null),
            buildCostBreakdown(current, totalCosts)
        );
    }

    public List<PnlSummaryResponse> getSummary(long workspaceId, PnlFilter filter) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (connectionIds.isEmpty()) {
            return List.of();
        }
        return pnlReadRepository.findSummary(connectionIds, filter);
    }

    public Page<ProductPnlResponse> getByProduct(long workspaceId, PnlFilter filter, Pageable pageable) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (connectionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        String sortColumn = extractSortColumn(pageable, "revenue_amount");
        List<ProductPnlResponse> content = pnlReadRepository.findByProduct(
                connectionIds, filter, sortColumn, pageable.getPageSize(), pageable.getOffset());
        long total = pnlReadRepository.countByProduct(connectionIds, filter);

        return new PageImpl<>(content, pageable, total);
    }

    public Page<PostingPnlResponse> getByPosting(long workspaceId, PnlFilter filter, Pageable pageable) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (connectionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        String sortColumn = extractSortColumn(pageable, "finance_date");
        List<PostingPnlResponse> content = pnlReadRepository.findByPosting(
                connectionIds, filter, sortColumn, pageable.getPageSize(), pageable.getOffset());
        long total = pnlReadRepository.countByPosting(connectionIds, filter);

        return new PageImpl<>(content, pageable, total);
    }

    public PostingPnlDetailResponse getPostingDetails(long workspaceId, String postingId) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (connectionIds.isEmpty()) {
            return new PostingPnlDetailResponse(
                    postingId, null, null, null, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                    BigDecimal.ZERO, List.of());
        }
        return pnlReadRepository.findPostingDetail(connectionIds, postingId);
    }

    public List<PnlTrendResponse> getTrend(long workspaceId, PnlFilter filter,
                                            TrendGranularity granularity) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (connectionIds.isEmpty()) {
            return List.of();
        }
        return pnlReadRepository.findTrend(connectionIds, filter, granularity);
    }

    private List<Long> resolveConnectionIds(long workspaceId) {
        return connectionRepository.findConnectionIdsByWorkspaceId(workspaceId);
    }

    private String extractSortColumn(Pageable pageable, String defaultColumn) {
        if (pageable.getSort().isSorted()) {
            return pageable.getSort().iterator().next().getProperty();
        }
        return defaultColumn;
    }

    private int parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            YearMonth now = YearMonth.now();
            return now.getYear() * 100 + now.getMonthValue();
        }
        try {
            YearMonth ym = YearMonth.parse(period);
            return ym.getYear() * 100 + ym.getMonthValue();
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    private int previousPeriod(int period) {
        int year = period / 100;
        int month = period % 100;
        if (month == 1) {
            return (year - 1) * 100 + 12;
        }
        return year * 100 + (month - 1);
    }

    private BigDecimal sumCosts(PnlAggregatedRow row) {
        return orZero(row.getMarketplaceCommissionAmount())
            .add(orZero(row.getAcquiringCommissionAmount()))
            .add(orZero(row.getLogisticsCostAmount()))
            .add(orZero(row.getStorageCostAmount()))
            .add(orZero(row.getPenaltiesAmount()))
            .add(orZero(row.getMarketingCostAmount()))
            .add(orZero(row.getAcceptanceCostAmount()))
            .add(orZero(row.getOtherMarketplaceChargesAmount()));
    }

    private BigDecimal deltaPct(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal cur = orZero(current);
        return cur.subtract(previous)
            .divide(previous.abs(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    private List<CostBreakdownItem> buildCostBreakdown(PnlAggregatedRow row,
                                                        BigDecimal totalCosts) {
        List<CostBreakdownItem> items = new ArrayList<>();
        addBreakdownItem(items, "marketplace_commission",
            row.getMarketplaceCommissionAmount(), totalCosts);
        addBreakdownItem(items, "acquiring_commission",
            row.getAcquiringCommissionAmount(), totalCosts);
        addBreakdownItem(items, "logistics", row.getLogisticsCostAmount(), totalCosts);
        addBreakdownItem(items, "storage", row.getStorageCostAmount(), totalCosts);
        addBreakdownItem(items, "penalties", row.getPenaltiesAmount(), totalCosts);
        addBreakdownItem(items, "marketing", row.getMarketingCostAmount(), totalCosts);
        addBreakdownItem(items, "acceptance", row.getAcceptanceCostAmount(), totalCosts);
        addBreakdownItem(items, "other", row.getOtherMarketplaceChargesAmount(), totalCosts);
        return items;
    }

    private void addBreakdownItem(List<CostBreakdownItem> items, String category,
                                   BigDecimal amount, BigDecimal total) {
        BigDecimal val = orZero(amount);
        if (val.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal pct = total.compareTo(BigDecimal.ZERO) != 0
            ? val.divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;
        items.add(new CostBreakdownItem(category, val, pct));
    }

    private PnlAggregatedSummaryResponse emptyAggregatedSummary() {
        return new PnlAggregatedSummaryResponse(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            null, null, null, null, null, List.of());
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
