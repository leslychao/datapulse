package io.datapulse.analytics.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.config.ClickHouseReadJdbc;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DataQualityReadRepository {

  private final ClickHouseReadJdbc jdbc;

  public boolean pingClickHouse() {
    jdbc.ch().getJdbcTemplate().queryForObject("SELECT 1", Integer.class);
    return true;
  }

  private static final String RECONCILIATION_SQL = """
      SELECT
          connection_id,
          source_platform,
          toYYYYMM(finance_date) AS period,
          sum(net_payout) AS total_net_payout,
          sum(reconciliation_residual) AS total_residual
      FROM mart_posting_pnl
      WHERE workspace_id = :workspaceId
      GROUP BY connection_id, source_platform, period
      ORDER BY connection_id, period DESC
      SETTINGS final = 1
      """;

  private static final String BASELINE_SQL = """
      SELECT
          connection_id,
          source_platform,
          avg(abs_residual_ratio) AS baseline_ratio,
          stddevPop(abs_residual_ratio) AS residual_std,
          count() AS period_count
      FROM (
          SELECT
              connection_id,
              source_platform,
              toYYYYMM(finance_date) AS period,
              abs(sum(reconciliation_residual))
                  / nullIf(abs(sum(net_payout)), 0) AS abs_residual_ratio
          FROM mart_posting_pnl
          WHERE workspace_id = :workspaceId
          GROUP BY connection_id, source_platform, period
      )
      GROUP BY connection_id, source_platform
      SETTINGS final = 1
      """;

  public List<ReconciliationRow> findReconciliationRows(long workspaceId) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    return jdbc.ch().query(RECONCILIATION_SQL, params, (rs, rowNum) -> {
      long connId = rs.getLong("connection_id");
      String platform = rs.getString("source_platform");
      BigDecimal netPayout = rs.getBigDecimal("total_net_payout");
      BigDecimal residual = rs.getBigDecimal("total_residual");

      BigDecimal residualRatio = BigDecimal.ZERO;
      if (netPayout != null && netPayout.compareTo(BigDecimal.ZERO) != 0) {
        residualRatio = residual.abs()
            .divide(netPayout.abs(), 6, RoundingMode.HALF_UP);
      }

      return new ReconciliationRow(
          connId, platform, rs.getInt("period"),
          netPayout, residual, residualRatio);
    });
  }

  public Map<String, BaselineStat> findBaselineStats(long workspaceId) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    var rows = jdbc.ch().query(BASELINE_SQL, params,
        (rs, rowNum) -> Map.entry(
            rs.getLong("connection_id") + ":" + rs.getString("source_platform"),
            new BaselineStat(
                rs.getBigDecimal("baseline_ratio"),
                rs.getBigDecimal("residual_std"),
                rs.getInt("period_count")
            )
        ));

    var result = new HashMap<String, BaselineStat>();
    for (var entry : rows) {
      result.put(entry.getKey(), entry.getValue());
    }
    return result;
  }

  public record ReconciliationRow(
      long connectionId,
      String sourcePlatform,
      int period,
      BigDecimal netPayout,
      BigDecimal residual,
      BigDecimal residualRatio
  ) {}

  public record BaselineStat(
      BigDecimal mean,
      BigDecimal std,
      int periodCount
  ) {

    public static final BaselineStat EMPTY = new BaselineStat(null, null, 0);
  }
}
