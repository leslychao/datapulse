package io.datapulse.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

import io.datapulse.analytics.api.ProductReturnResponse;
import io.datapulse.analytics.api.ReturnsFilter;
import io.datapulse.analytics.api.ReturnsSummaryResponse;
import io.datapulse.analytics.api.ReturnsSummaryResponse.ReasonBreakdownItem;
import io.datapulse.analytics.api.ReturnsTrendResponse;
import io.datapulse.analytics.persistence.ReturnsReadRepository;
import io.datapulse.analytics.persistence.ReturnsReadRepository.ReasonRow;
import io.datapulse.analytics.persistence.ReturnsReadRepository.SummaryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReturnsAnalysisService {

  private final ReturnsReadRepository returnsReadRepository;

  public ReturnsSummaryResponse getSummary(long workspaceId, ReturnsFilter filter) {
    SummaryRow summary = returnsReadRepository.findSummary(workspaceId, filter);

    Integer currentPeriod = filter.periodAsInt();
    List<ReasonBreakdownItem> reasonBreakdown = List.of();
    BigDecimal returnRateDeltaPct = null;

    if (currentPeriod != null) {
      reasonBreakdown = buildReasonBreakdown(workspaceId, currentPeriod);
      returnRateDeltaPct = calculateDelta(workspaceId, currentPeriod, summary.returnRatePct());
    }

    return new ReturnsSummaryResponse(
        summary.returnRatePct(),
        returnRateDeltaPct,
        summary.returnAmount(),
        summary.totalReturnCount(),
        summary.topReturnReason(),
        reasonBreakdown
    );
  }

  public Page<ProductReturnResponse> getByProduct(long workspaceId, ReturnsFilter filter,
      Pageable pageable) {
    String sortColumn = pageable.getSort().isSorted()
        ? pageable.getSort().iterator().next().getProperty()
        : "returnRatePct";

    List<ProductReturnResponse> content = returnsReadRepository.findByProduct(
        workspaceId, filter, sortColumn, pageable.getPageSize(), pageable.getOffset());
    long total = returnsReadRepository.countByProduct(workspaceId, filter);

    return new PageImpl<>(content, pageable, total);
  }

  public List<ReturnsTrendResponse> getTrend(long workspaceId, ReturnsFilter filter) {
    return returnsReadRepository.findTrend(workspaceId, filter);
  }

  private List<ReasonBreakdownItem> buildReasonBreakdown(long workspaceId, int period) {
    List<ReasonRow> rows = returnsReadRepository.findReasonBreakdown(workspaceId, period);
    int totalCount = rows.stream().mapToInt(ReasonRow::count).sum();

    if (totalCount == 0) {
      return List.of();
    }

    return rows.stream()
        .map(r -> new ReasonBreakdownItem(
            r.reason(),
            r.count(),
            BigDecimal.valueOf(r.count())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalCount), 1, RoundingMode.HALF_UP)))
        .toList();
  }

  private BigDecimal calculateDelta(long workspaceId, int currentPeriod,
      BigDecimal currentRate) {
    if (currentRate == null) {
      return null;
    }

    YearMonth current = YearMonth.of(currentPeriod / 100, currentPeriod % 100);
    YearMonth previous = current.minusMonths(1);
    int previousPeriod = previous.getYear() * 100 + previous.getMonthValue();

    BigDecimal previousRate = returnsReadRepository
        .findReturnRateForPeriod(workspaceId, previousPeriod);

    if (previousRate == null) {
      return null;
    }

    return currentRate.subtract(previousRate).setScale(2, RoundingMode.HALF_UP);
  }
}
