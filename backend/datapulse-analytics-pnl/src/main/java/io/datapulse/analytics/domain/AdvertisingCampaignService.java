package io.datapulse.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.api.CampaignDashboardFilter;
import io.datapulse.analytics.api.CampaignSummaryResponse;
import io.datapulse.analytics.persistence.AdvertisingCampaignReadRepository;
import io.datapulse.analytics.persistence.AdvertisingClickHouseReadRepository;
import io.datapulse.analytics.persistence.CampaignMetrics;
import io.datapulse.analytics.persistence.CampaignPgRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdvertisingCampaignService {

  private final AdvertisingCampaignReadRepository pgRepo;
  private final AdvertisingClickHouseReadRepository chRepo;

  public Page<CampaignSummaryResponse> getCampaigns(
      long workspaceId, CampaignDashboardFilter filter, Pageable pageable) {

    String sortColumn = extractSortColumn(pageable, "name");

    List<CampaignPgRow> pgRows = pgRepo.findCampaigns(
        workspaceId, filter.sourcePlatform(), filter.status(),
        sortColumn, pageable.getPageSize(), pageable.getOffset());
    long total = pgRepo.countCampaigns(
        workspaceId, filter.sourcePlatform(), filter.status());

    if (pgRows.isEmpty()) {
      return new PageImpl<>(List.of(), pageable, total);
    }

    List<Long> campaignIds = parseCampaignIds(pgRows);
    int periodDays = filter.periodDays();

    Map<String, CampaignMetrics> metricsMap =
        chRepo.findCampaignMetrics(workspaceId, campaignIds, periodDays);

    List<CampaignSummaryResponse> content = pgRows.stream()
        .map(row -> toResponse(row, metricsMap))
        .toList();

    return new PageImpl<>(content, pageable, total);
  }

  private List<Long> parseCampaignIds(List<CampaignPgRow> rows) {
    List<Long> ids = new ArrayList<>();
    for (CampaignPgRow row : rows) {
      try {
        ids.add(Long.parseLong(row.externalCampaignId()));
      } catch (NumberFormatException ignored) {
        // non-numeric external IDs are skipped for CH enrichment
      }
    }
    return ids;
  }

  private CampaignSummaryResponse toResponse(CampaignPgRow row,
      Map<String, CampaignMetrics> metricsMap) {
    String key = row.connectionId() + ":" + row.externalCampaignId();
    CampaignMetrics m = metricsMap.get(key);

    BigDecimal spendForPeriod = null;
    Integer ordersForPeriod = null;
    BigDecimal drrPct = null;
    String drrTrend = null;

    if (m != null) {
      spendForPeriod = m.currentSpend();
      ordersForPeriod = m.currentOrders();
      drrPct = computeDrr(m.currentSpend(), m.currentRevenue());

      BigDecimal prevDrr = computeDrr(m.prevSpend(), m.prevRevenue());
      drrTrend = computeTrend(drrPct, prevDrr);
    }

    return new CampaignSummaryResponse(
        row.id(),
        row.externalCampaignId(),
        row.name(),
        row.sourcePlatform(),
        row.campaignType(),
        row.status(),
        row.dailyBudget(),
        spendForPeriod,
        ordersForPeriod,
        drrPct,
        drrTrend
    );
  }

  private BigDecimal computeDrr(BigDecimal spend, BigDecimal revenue) {
    if (spend == null || revenue == null
        || revenue.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }
    return spend.divide(revenue, 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100))
        .setScale(2, RoundingMode.HALF_UP);
  }

  private String computeTrend(BigDecimal current, BigDecimal previous) {
    if (current == null || previous == null) {
      return "FLAT";
    }
    int cmp = current.compareTo(previous);
    if (cmp > 0) return "UP";
    if (cmp < 0) return "DOWN";
    return "FLAT";
  }

  private String extractSortColumn(Pageable pageable, String defaultColumn) {
    if (pageable.getSort().isSorted()) {
      return pageable.getSort().iterator().next().getProperty();
    }
    return defaultColumn;
  }
}
