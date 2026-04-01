package io.datapulse.analytics.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.api.ReconciliationResponse;
import io.datapulse.analytics.config.ClickHouseReadJdbc;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DataQualityReadRepository {

    private final ClickHouseReadJdbc jdbc;

    private static final String RECONCILIATION_SQL = """
            SELECT
                connection_id,
                source_platform,
                toYYYYMM(finance_date) AS period,
                sum(net_payout) AS total_net_payout,
                sum(revenue_amount) + sum(marketplace_commission_amount)
                    + sum(acquiring_commission_amount) + sum(logistics_cost_amount)
                    + sum(storage_cost_amount) + sum(penalties_amount)
                    + sum(marketing_cost_amount) + sum(acceptance_cost_amount)
                    + sum(other_marketplace_charges_amount) + sum(compensation_amount)
                    + sum(refund_amount) AS total_measures_sum,
                sum(reconciliation_residual) AS total_residual
            FROM mart_posting_pnl
            WHERE connection_id IN (:connectionIds)
            GROUP BY connection_id, source_platform, period
            ORDER BY connection_id, period DESC
            SETTINGS final = 1
            """;

    private static final String BASELINE_SQL = """
            SELECT
                connection_id,
                source_platform,
                avg(abs_residual_ratio) AS baseline_residual_ratio,
                stddevPop(abs_residual_ratio) AS residual_std
            FROM (
                SELECT
                    connection_id,
                    source_platform,
                    toYYYYMM(finance_date) AS period,
                    abs(sum(reconciliation_residual)) / nullIf(abs(sum(net_payout)), 0) AS abs_residual_ratio
                FROM mart_posting_pnl
                WHERE connection_id IN (:connectionIds)
                GROUP BY connection_id, source_platform, period
            )
            GROUP BY connection_id, source_platform
            SETTINGS final = 1
            """;

    public List<ReconciliationResponse> findReconciliation(List<Long> connectionIds, int stdMultiplier) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);

        var baselines = jdbc.ch().query(BASELINE_SQL, params,
                (rs, rowNum) -> Map.entry(
                        rs.getLong("connection_id") + ":" + rs.getString("source_platform"),
                        new BaselineStat(
                                rs.getBigDecimal("baseline_residual_ratio"),
                                rs.getBigDecimal("residual_std")
                        )
                ));

        var baselineMap = new java.util.HashMap<String, BaselineStat>();
        for (var entry : baselines) {
            baselineMap.put(entry.getKey(), entry.getValue());
        }

        return jdbc.ch().query(RECONCILIATION_SQL, params, (rs, rowNum) -> {
            long connectionId = rs.getLong("connection_id");
            String platform = rs.getString("source_platform");
            BigDecimal netPayout = rs.getBigDecimal("total_net_payout");
            BigDecimal residual = rs.getBigDecimal("total_residual");

            BigDecimal residualRatio = BigDecimal.ZERO;
            if (netPayout != null && netPayout.compareTo(BigDecimal.ZERO) != 0) {
                residualRatio = residual.abs()
                        .divide(netPayout.abs(), 6, java.math.RoundingMode.HALF_UP);
            }

            String key = connectionId + ":" + platform;
            BaselineStat baseline = baselineMap.getOrDefault(key, BaselineStat.EMPTY);
            boolean anomaly = isAnomaly(residualRatio, baseline, stdMultiplier);

            return new ReconciliationResponse(
                    connectionId,
                    platform,
                    rs.getInt("period"),
                    netPayout,
                    rs.getBigDecimal("total_measures_sum"),
                    residual,
                    residualRatio,
                    baseline.mean(),
                    anomaly
            );
        });
    }

    private boolean isAnomaly(BigDecimal currentRatio, BaselineStat baseline, int stdMultiplier) {
        if (baseline.mean() == null || baseline.std() == null) {
            return false;
        }
        BigDecimal threshold = baseline.mean()
                .add(baseline.std().multiply(BigDecimal.valueOf(stdMultiplier)));
        return currentRatio.compareTo(threshold) > 0;
    }

    private record BaselineStat(BigDecimal mean, BigDecimal std) {
        static final BaselineStat EMPTY = new BaselineStat(null, null);
    }
}
