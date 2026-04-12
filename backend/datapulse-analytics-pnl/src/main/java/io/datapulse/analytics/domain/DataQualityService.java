package io.datapulse.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.api.DataQualityStatusResponse;
import io.datapulse.analytics.api.DataQualityStatusResponse.ConnectionDataQuality;
import io.datapulse.analytics.api.DataQualityStatusResponse.SyncDomainInfo;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.analytics.api.ReconciliationResultResponse;
import io.datapulse.analytics.api.ReconciliationResultResponse.ConnectionReconciliation;
import io.datapulse.analytics.api.ReconciliationResultResponse.ResidualBucket;
import io.datapulse.analytics.api.ReconciliationResultResponse.TrendPoint;
import io.datapulse.analytics.config.AnalyticsQueryProperties;
import io.datapulse.analytics.config.AnalyticsQueryProperties.DataQualityProperties;
import io.datapulse.analytics.persistence.DataQualityReadRepository;
import io.datapulse.analytics.persistence.DataQualityReadRepository.BaselineStat;
import io.datapulse.analytics.persistence.DataQualityReadRepository.ReconciliationRow;
import io.datapulse.analytics.persistence.SyncStateReadRepository;
import io.datapulse.analytics.persistence.SyncStateReadRepository.SyncFreshnessRow;
import io.datapulse.analytics.persistence.WorkspaceConnectionRepository;
import io.datapulse.analytics.persistence.WorkspaceConnectionRepository.ConnectionRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DataQualityService {

  private static final int OVERDUE_MULTIPLIER = 3;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private static final BigDecimal[] BUCKET_BOUNDARIES = {
      BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("2"),
      new BigDecimal("5"), new BigDecimal("10"),
      new BigDecimal("20"), new BigDecimal("50"), HUNDRED
  };

  private final DataQualityReadRepository dataQualityReadRepository;
  private final SyncStateReadRepository syncStateReadRepository;
  private final WorkspaceConnectionRepository connectionRepository;
  private final AnalyticsQueryProperties properties;

  // ── Status ──────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public DataQualityStatusResponse getStatus(long workspaceId) {
    List<SyncFreshnessRow> rows = syncStateReadRepository.findSyncFreshness(workspaceId);
    if (rows.isEmpty()) {
      return new DataQualityStatusResponse(List.of());
    }

    OffsetDateTime now = OffsetDateTime.now();
    DataQualityProperties dqProps = properties.dataQuality();

    var grouped = new LinkedHashMap<Long, ConnectionBuilder>();

    for (SyncFreshnessRow row : rows) {
      var builder = grouped.computeIfAbsent(row.connectionId(),
          id -> new ConnectionBuilder(id, row.connectionName(), row.sourcePlatform()));

      int thresholdHours = resolveThresholdHours(row.dataDomain(), dqProps);
      String status = computeSyncStatus(row.lastSuccessAt(), thresholdHours, now);

      builder.domains.add(new SyncDomainInfo(
          row.dataDomain(), row.lastSuccessAt(), status));

      if (isBlockingDomain(row.dataDomain()) && !"FRESH".equals(status)) {
        builder.automationBlocked = true;
        builder.blockReason = MessageCodes.DATA_QUALITY_STALE_DOMAIN;
        builder.blockReasonArgs = Map.of(
            "domain", row.dataDomain(),
            "hours", thresholdHours);
      }
    }

    List<ConnectionDataQuality> connections = grouped.values().stream()
        .map(b -> new ConnectionDataQuality(
            b.connectionName, b.marketplaceType,
            b.automationBlocked, b.blockReason, b.blockReasonArgs,
            List.copyOf(b.domains)))
        .toList();

    return new DataQualityStatusResponse(connections);
  }

  // ── Reconciliation ──────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public ReconciliationResultResponse getReconciliation(
      long workspaceId, Integer periodFilter) {

    Map<Long, ConnectionRow> connMap =
        connectionRepository.findActiveByWorkspaceIdAsMap(workspaceId);
    if (connMap.isEmpty()) {
      return new ReconciliationResultResponse(List.of(), List.of(), List.of());
    }

    List<ReconciliationRow> allRows =
        dataQualityReadRepository.findReconciliationRows(workspaceId);
    Map<String, BaselineStat> baselines =
        dataQualityReadRepository.findBaselineStats(workspaceId);

    if (allRows.isEmpty()) {
      return new ReconciliationResultResponse(List.of(), List.of(), List.of());
    }

    DataQualityProperties dqProps = properties.dataQuality();
    int stdMultiplier = dqProps.residualAnomalyStdMultiplier();
    int minSamples = dqProps.residualMinSampleSize();

    List<ConnectionReconciliation> connections = buildConnectionKpis(
        allRows, connMap, baselines, periodFilter, stdMultiplier, minSamples);

    List<TrendPoint> trend = buildTrend(allRows, baselines);

    var distributionRows = periodFilter != null
        ? allRows.stream().filter(r -> r.period() == periodFilter).toList()
        : allRows;
    List<ResidualBucket> distribution = buildDistribution(distributionRows);

    return new ReconciliationResultResponse(connections, trend, distribution);
  }

  public boolean isClickHouseAvailable() {
    try {
      return dataQualityReadRepository.pingClickHouse();
    } catch (Exception e) {
      return false;
    }
  }

  // ── Private: Status helpers ─────────────────────────────────────────

  String computeSyncStatus(
      OffsetDateTime lastSuccessAt, int thresholdHours, OffsetDateTime now) {
    if (lastSuccessAt == null) {
      return "OVERDUE";
    }
    if (lastSuccessAt.plusHours(thresholdHours).isAfter(now)) {
      return "FRESH";
    }
    if (lastSuccessAt.plusHours((long) thresholdHours * OVERDUE_MULTIPLIER).isAfter(now)) {
      return "STALE";
    }
    return "OVERDUE";
  }

  private int resolveThresholdHours(String domain, DataQualityProperties dqProps) {
    return switch (domain.toLowerCase()) {
      case "finance" -> dqProps.staleFinanceThresholdHours();
      default -> dqProps.staleStateThresholdHours();
    };
  }

  private boolean isBlockingDomain(String domain) {
    return "finance".equalsIgnoreCase(domain);
  }

  // ── Private: Reconciliation helpers ─────────────────────────────────

  private List<ConnectionReconciliation> buildConnectionKpis(
      List<ReconciliationRow> allRows,
      Map<Long, ConnectionRow> connMap,
      Map<String, BaselineStat> baselines,
      Integer periodFilter,
      int stdMultiplier,
      int minSamples) {

    var kpiRows = periodFilter != null
        ? allRows.stream().filter(r -> r.period() == periodFilter).toList()
        : latestPeriodPerConnection(allRows);

    var grouped = new LinkedHashMap<Long, List<ReconciliationRow>>();
    for (var row : kpiRows) {
      grouped.computeIfAbsent(row.connectionId(), k -> new ArrayList<>()).add(row);
    }

    var result = new ArrayList<ConnectionReconciliation>();
    for (var entry : grouped.entrySet()) {
      long connId = entry.getKey();
      List<ReconciliationRow> rows = entry.getValue();
      ConnectionRow conn = connMap.get(connId);
      if (conn == null) continue;

      BigDecimal totalResidual = BigDecimal.ZERO;
      BigDecimal totalNetPayout = BigDecimal.ZERO;
      for (var row : rows) {
        totalResidual = totalResidual.add(
            row.residual() != null ? row.residual() : BigDecimal.ZERO);
        totalNetPayout = totalNetPayout.add(
            row.netPayout() != null ? row.netPayout() : BigDecimal.ZERO);
      }

      BigDecimal residualRatioPct = BigDecimal.ZERO;
      if (totalNetPayout.compareTo(BigDecimal.ZERO) != 0) {
        residualRatioPct = totalResidual.abs()
            .divide(totalNetPayout.abs(), 6, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(2, RoundingMode.HALF_UP);
      }

      String key = connId + ":" + conn.marketplaceType().toLowerCase();
      BaselineStat baseline = baselines.getOrDefault(key, BaselineStat.EMPTY);

      BigDecimal baselinePct = baseline.mean() != null
          ? baseline.mean().multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
          : BigDecimal.ZERO;

      String status = resolveReconStatus(
          residualRatioPct, baseline, stdMultiplier, minSamples);

      result.add(new ConnectionReconciliation(
          conn.name(), conn.marketplaceType(),
          totalResidual, residualRatioPct, baselinePct, status));
    }
    return result;
  }

  private List<ReconciliationRow> latestPeriodPerConnection(
      List<ReconciliationRow> rows) {
    var latest = new LinkedHashMap<String, Integer>();
    for (var row : rows) {
      String key = row.connectionId() + ":" + row.sourcePlatform();
      latest.merge(key, row.period(), Math::max);
    }
    return rows.stream()
        .filter(r -> {
          String key = r.connectionId() + ":" + r.sourcePlatform();
          return r.period() == latest.getOrDefault(key, 0);
        })
        .toList();
  }

  private List<TrendPoint> buildTrend(
      List<ReconciliationRow> rows,
      Map<String, BaselineStat> baselines) {

    return rows.stream().map(row -> {
      BigDecimal ratioPct = row.residualRatio()
          .multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);

      String key = row.connectionId() + ":" + row.sourcePlatform().toLowerCase();
      BaselineStat baseline = baselines.getOrDefault(key, BaselineStat.EMPTY);
      BigDecimal baselinePct = baseline.mean() != null
          ? baseline.mean().multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
          : BigDecimal.ZERO;

      return new TrendPoint(
          String.valueOf(row.period()),
          row.sourcePlatform(),
          ratioPct,
          baselinePct);
    }).toList();
  }

  private List<ResidualBucket> buildDistribution(List<ReconciliationRow> rows) {
    var buckets = new ArrayList<ResidualBucket>();
    for (int i = 0; i < BUCKET_BOUNDARIES.length; i++) {
      BigDecimal from = BUCKET_BOUNDARIES[i];
      boolean isLast = i == BUCKET_BOUNDARIES.length - 1;
      BigDecimal to = isLast ? new BigDecimal("999") : BUCKET_BOUNDARIES[i + 1];
      String label = isLast
          ? from + "%+"
          : from + "–" + to + "%";

      final BigDecimal bFrom = from;
      final BigDecimal bTo = to;
      final boolean last = isLast;

      int count = (int) rows.stream()
          .map(r -> r.residualRatio().multiply(HUNDRED))
          .filter(pct -> pct.compareTo(bFrom) >= 0
              && (last || pct.compareTo(bTo) < 0))
          .count();

      buckets.add(new ResidualBucket(label, count, from, to));
    }
    return buckets;
  }

  private String resolveReconStatus(
      BigDecimal currentRatioPct,
      BaselineStat baseline,
      int stdMultiplier,
      int minSamples) {
    if (baseline.periodCount() < minSamples) {
      return baseline.periodCount() == 0 ? "INSUFFICIENT_DATA" : "CALIBRATION";
    }
    if (baseline.mean() == null || baseline.std() == null) {
      return "INSUFFICIENT_DATA";
    }
    BigDecimal thresholdPct = baseline.mean()
        .add(baseline.std().multiply(BigDecimal.valueOf(stdMultiplier)))
        .multiply(HUNDRED);
    return currentRatioPct.compareTo(thresholdPct) > 0 ? "ANOMALY" : "NORMAL";
  }

  // ── Inner builder ───────────────────────────────────────────────────

  private static class ConnectionBuilder {

    final long connectionId;
    final String connectionName;
    final String marketplaceType;
    final List<SyncDomainInfo> domains = new ArrayList<>();
    boolean automationBlocked;
    String blockReason;
    Map<String, Object> blockReasonArgs;

    ConnectionBuilder(long connectionId, String connectionName,
        String marketplaceType) {
      this.connectionId = connectionId;
      this.connectionName = connectionName;
      this.marketplaceType = marketplaceType;
    }
  }
}
