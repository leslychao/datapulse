package io.datapulse.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import io.datapulse.analytics.api.PnlAggregatedSummaryResponse;
import io.datapulse.analytics.api.PnlAggregatedSummaryResponse.CostBreakdownItem;
import io.datapulse.analytics.api.PnlFilter;
import io.datapulse.analytics.api.PnlTrendResponse;
import io.datapulse.analytics.api.PostingPnlDetailResponse;
import io.datapulse.analytics.api.PostingPnlResponse;
import io.datapulse.analytics.api.ProductPnlResponse;
import io.datapulse.analytics.api.TrendGranularity;
import io.datapulse.analytics.persistence.PnlAggregatedRow;
import io.datapulse.analytics.persistence.PnlReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PnlQueryService {

  private final PnlReadRepository pnlReadRepository;

  public PnlAggregatedSummaryResponse getAggregatedSummary(long workspaceId, PnlFilter filter) {
    int currentPeriod = parsePeriod(filter.period());
    if (currentPeriod == 0) {
      return emptyAggregatedSummary();
    }

    PnlAggregatedRow current = pnlReadRepository.findAggregatedSummary(
        workspaceId, currentPeriod, filter);
    if (current == null) {
      return emptyAggregatedSummary();
    }

    int prevPeriod = previousPeriod(currentPeriod);
    PnlAggregatedRow prev = pnlReadRepository.findAggregatedSummary(
        workspaceId, prevPeriod, filter);

    BigDecimal totalCosts = sumCosts(current);
    BigDecimal prevTotalCosts = prev != null ? sumCosts(prev) : null;

    BigDecimal residual = pnlReadRepository.findReconciliationResidual(
        workspaceId, currentPeriod, filter);
    BigDecimal netPayout = orZero(current.getNetPayout());
    BigDecimal reconciliationRatio = netPayout.compareTo(BigDecimal.ZERO) != 0
        ? orZero(residual).abs()
            .divide(netPayout.abs(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
        : BigDecimal.ZERO;

    BigDecimal revenue = orZero(current.getRevenueAmount());
    BigDecimal compensation = orZero(current.getCompensationAmount());
    BigDecimal refund = orZero(current.getRefundAmount());
    BigDecimal marketplacePnl = orZero(current.getMarketplacePnl());
    BigDecimal cogs = orZero(current.getNetCogs());
    BigDecimal advertising = orZero(current.getAdvertisingCost());
    BigDecimal fullPnl = marketplacePnl.subtract(advertising).subtract(cogs);

    BigDecimal prevFullPnl = prev != null
        ? computeFullPnl(prev)
        : null;

    return new PnlAggregatedSummaryResponse(
        revenue,
        totalCosts,
        compensation,
        refund,
        cogs,
        advertising,
        marketplacePnl,
        fullPnl,
        orZero(residual),
        reconciliationRatio,
        deltaPct(current.getRevenueAmount(), prev != null ? prev.getRevenueAmount() : null),
        deltaPct(totalCosts, prevTotalCosts),
        deltaPct(compensation, prev != null ? prev.getCompensationAmount() : null),
        deltaPct(refund, prev != null ? prev.getRefundAmount() : null),
        deltaPct(cogs, prev != null ? prev.getNetCogs() : null),
        deltaPct(advertising, prev != null ? prev.getAdvertisingCost() : null),
        deltaPct(fullPnl, prevFullPnl),
        buildCostBreakdown(current, totalCosts)
    );
  }

  public Page<ProductPnlResponse> getByProduct(long workspaceId, PnlFilter filter,
      Pageable pageable) {
    Sort.Order order = pageable.getSort().isSorted()
        ? pageable.getSort().iterator().next()
        : Sort.Order.desc("revenueAmount");

    String sortColumn = camelToSnake(order.getProperty());
    String sortDirection = order.getDirection().name();

    List<ProductPnlResponse> content = pnlReadRepository.findByProduct(
        workspaceId, filter, sortColumn, sortDirection,
        pageable.getPageSize(), pageable.getOffset());
    long total = pnlReadRepository.countByProduct(workspaceId, filter);

    return new PageImpl<>(content, pageable, total);
  }

  public Page<PostingPnlResponse> getByPosting(long workspaceId, PnlFilter filter,
      Pageable pageable) {
    Sort.Order order = pageable.getSort().isSorted()
        ? pageable.getSort().iterator().next()
        : Sort.Order.desc("financeDate");

    String sortColumn = camelToSnake(order.getProperty());
    String sortDirection = order.getDirection().name();

    List<PostingPnlResponse> content = pnlReadRepository.findByPosting(
        workspaceId, filter, sortColumn, sortDirection,
        pageable.getPageSize(), pageable.getOffset());
    long total = pnlReadRepository.countByPosting(workspaceId, filter);

    return new PageImpl<>(content, pageable, total);
  }

  public PostingPnlDetailResponse getPostingDetails(long workspaceId, String postingId) {
    return pnlReadRepository.findPostingDetail(workspaceId, postingId);
  }

  public List<PnlTrendResponse> getTrend(long workspaceId, PnlFilter filter,
      TrendGranularity granularity) {
    PnlFilter bounded = ensureTrendBounds(filter, granularity);
    return pnlReadRepository.findTrend(workspaceId, bounded, granularity);
  }

  private BigDecimal computeFullPnl(PnlAggregatedRow row) {
    return orZero(row.getMarketplacePnl())
        .subtract(orZero(row.getAdvertisingCost()))
        .subtract(orZero(row.getNetCogs()));
  }

  private PnlFilter ensureTrendBounds(PnlFilter filter, TrendGranularity granularity) {
    if (filter.from() != null || filter.to() != null) {
      return filter;
    }
    YearMonth anchor = filter.period() != null && !filter.period().isBlank()
        ? parseYearMonth(filter.period())
        : YearMonth.now();
    if (anchor == null) {
      anchor = YearMonth.now();
    }
    LocalDate to = anchor.atEndOfMonth();
    int monthsBack = granularity == TrendGranularity.MONTHLY ? 11 : 2;
    LocalDate from = anchor.minusMonths(monthsBack).atDay(1);
    return new PnlFilter(from, to, filter.period(), filter.sellerSkuId(),
        filter.search(), filter.sourcePlatform());
  }

  private YearMonth parseYearMonth(String period) {
    try {
      return YearMonth.parse(period);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String camelToSnake(String camel) {
    return camel.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
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

  /**
   * Costs in the mart are stored as negative (signed convention).
   * For display purposes we return the absolute sum.
   */
  private BigDecimal sumCosts(PnlAggregatedRow row) {
    return orZero(row.getMarketplaceCommissionAmount())
        .add(orZero(row.getAcquiringCommissionAmount()))
        .add(orZero(row.getLogisticsCostAmount()))
        .add(orZero(row.getStorageCostAmount()))
        .add(orZero(row.getPenaltiesAmount()))
        .add(orZero(row.getMarketingCostAmount()))
        .add(orZero(row.getAcceptanceCostAmount()))
        .add(orZero(row.getOtherMarketplaceChargesAmount()))
        .abs();
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
    BigDecimal val = orZero(amount).abs();
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
        BigDecimal.ZERO, BigDecimal.ZERO,
        null, null, null, null, null, null, null, List.of());
  }

  private BigDecimal orZero(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
