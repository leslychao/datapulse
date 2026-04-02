package io.datapulse.sellerops.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class GridClickHouseReadRepository {

    private final NamedParameterJdbcTemplate ch;

    public GridClickHouseReadRepository(
            @Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate) {
        this.ch = new NamedParameterJdbcTemplate(clickhouseJdbcTemplate);
    }

    private static final String ENRICHMENT_SQL = """
            SELECT
                inv.product_id              AS offer_id,
                inv.days_of_cover,
                inv.stock_out_risk,
                fin.revenue_30d,
                fin.net_pnl_30d,
                sales.velocity_14d,
                ret.return_rate_pct
            FROM (
                SELECT product_id, days_of_cover, stock_out_risk
                FROM mart_inventory_analysis
                WHERE product_id IN (:offerIds)
                  AND analysis_date = (
                      SELECT max(analysis_date) FROM mart_inventory_analysis
                      WHERE product_id IN (:offerIds)
                  )
            ) inv
            LEFT JOIN (
                SELECT
                    dp.product_id,
                    sum(ff.revenue_amount) AS revenue_30d,
                    sumIf(ff.net_payout,
                          ff.attribution_level IN ('POSTING', 'PRODUCT')) AS net_pnl_30d
                FROM fact_finance AS ff
                JOIN dim_product AS dp
                    ON ff.seller_sku_id = dp.seller_sku_id
                    AND ff.connection_id = dp.connection_id
                WHERE dp.product_id IN (:offerIds)
                  AND ff.finance_date >= today() - 30
                GROUP BY dp.product_id
            ) fin ON inv.product_id = fin.product_id
            LEFT JOIN (
                SELECT
                    product_id,
                    toDecimal64(sum(quantity), 2) / 14 AS velocity_14d
                FROM fact_sales
                WHERE product_id IN (:offerIds)
                  AND sale_date >= today() - 14
                GROUP BY product_id
            ) sales ON inv.product_id = sales.product_id
            LEFT JOIN (
                SELECT product_id, return_rate_pct
                FROM mart_returns_analysis
                WHERE product_id IN (:offerIds)
                  AND period = (
                      SELECT max(period) FROM mart_returns_analysis
                      WHERE product_id IN (:offerIds)
                  )
            ) ret ON inv.product_id = ret.product_id
            SETTINGS final = 1
            """;

    private static final java.util.Set<String> CH_SORT_COLUMNS = java.util.Set.of(
            "revenue30d", "netPnl30d", "velocity14d",
            "returnRatePct", "daysOfCover", "stockRisk"
    );

    private static final java.util.Map<String, String> CH_SORT_COLUMN_MAP = java.util.Map.of(
            "revenue30d", "fin.revenue_30d",
            "netPnl30d", "fin.net_pnl_30d",
            "velocity14d", "sales.velocity_14d",
            "returnRatePct", "ret.return_rate_pct",
            "daysOfCover", "inv.days_of_cover",
            "stockRisk", "inv.stock_out_risk"
    );

    public boolean isChSortColumn(String column) {
        return CH_SORT_COLUMNS.contains(column);
    }

    public List<Long> findSortedOfferIds(List<Long> connectionIds, String sortColumn,
                                          String direction, int limit) {
        String chColumn = CH_SORT_COLUMN_MAP.get(sortColumn);
        if (chColumn == null || connectionIds == null || connectionIds.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT inv.product_id AS offer_id
                FROM mart_inventory_analysis inv
                LEFT JOIN (
                    SELECT dp.product_id,
                           sum(ff.revenue_amount) AS revenue_30d,
                           sumIf(ff.net_payout,
                                 ff.attribution_level IN ('POSTING','PRODUCT')) AS net_pnl_30d
                    FROM fact_finance ff
                    JOIN dim_product dp ON ff.seller_sku_id = dp.seller_sku_id
                        AND ff.connection_id = dp.connection_id
                    WHERE dp.connection_id IN (:connectionIds)
                      AND ff.finance_date >= today() - 30
                    GROUP BY dp.product_id
                ) fin ON inv.product_id = fin.product_id
                LEFT JOIN (
                    SELECT product_id, toDecimal64(sum(quantity),2)/14 AS velocity_14d
                    FROM fact_sales
                    WHERE connection_id IN (:connectionIds) AND sale_date >= today()-14
                    GROUP BY product_id
                ) sales ON inv.product_id = sales.product_id
                LEFT JOIN (
                    SELECT product_id, return_rate_pct
                    FROM mart_returns_analysis
                    WHERE connection_id IN (:connectionIds)
                      AND period = (SELECT max(period) FROM mart_returns_analysis
                                    WHERE connection_id IN (:connectionIds))
                ) ret ON inv.product_id = ret.product_id
                WHERE inv.connection_id IN (:connectionIds)
                  AND inv.analysis_date = (SELECT max(analysis_date)
                                           FROM mart_inventory_analysis
                                           WHERE connection_id IN (:connectionIds))
                ORDER BY %s %s NULLS LAST
                LIMIT :limit
                SETTINGS final = 1
                """.formatted(chColumn, direction);

        var params = new MapSqlParameterSource()
                .addValue("connectionIds", connectionIds)
                .addValue("limit", limit);

        return ch.queryForList(sql, params, Long.class);
    }

    public List<Long> findOfferIdsByStockRisk(List<Long> connectionIds, String stockRisk) {
        if (connectionIds == null || connectionIds.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT product_id
                FROM mart_inventory_analysis
                WHERE connection_id IN (:connectionIds)
                  AND stock_out_risk = :stockRisk
                  AND analysis_date = (SELECT max(analysis_date)
                                       FROM mart_inventory_analysis
                                       WHERE connection_id IN (:connectionIds))
                LIMIT 10000
                SETTINGS final = 1
                """;

        var params = new MapSqlParameterSource()
                .addValue("connectionIds", connectionIds)
                .addValue("stockRisk", stockRisk);

        return ch.queryForList(sql, params, Long.class);
    }

    public Map<Long, ClickHouseEnrichment> findEnrichment(List<Long> offerIds) {
        if (offerIds == null || offerIds.isEmpty()) {
            return Collections.emptyMap();
        }

        var params = new MapSqlParameterSource("offerIds", offerIds);

        List<ClickHouseEnrichment> rows = ch.query(ENRICHMENT_SQL, params, this::mapEnrichment);

        return rows.stream()
                .collect(Collectors.toMap(ClickHouseEnrichment::getOfferId, Function.identity()));
    }

    private static final String KPI_SQL = """
            SELECT
                inv.critical_stock_count,
                cur.revenue_30d_total,
                CASE
                    WHEN prev.revenue_prev_30d IS NOT NULL AND prev.revenue_prev_30d > 0
                    THEN round((cur.revenue_30d_total - prev.revenue_prev_30d)
                               / prev.revenue_prev_30d * 100, 2)
                    ELSE NULL
                END AS revenue_30d_trend
            FROM (
                SELECT countDistinct(product_id) AS critical_stock_count
                FROM mart_inventory_analysis
                WHERE connection_id IN (:connectionIds)
                  AND stock_out_risk = 'CRITICAL'
                  AND analysis_date = (
                      SELECT max(analysis_date)
                      FROM mart_inventory_analysis
                      WHERE connection_id IN (:connectionIds)
                  )
            ) inv
            CROSS JOIN (
                SELECT coalesce(sum(ff.revenue_amount), 0) AS revenue_30d_total
                FROM fact_finance AS ff
                WHERE ff.connection_id IN (:connectionIds)
                  AND ff.finance_date >= today() - 30
            ) cur
            CROSS JOIN (
                SELECT coalesce(sum(ff.revenue_amount), 0) AS revenue_prev_30d
                FROM fact_finance AS ff
                WHERE ff.connection_id IN (:connectionIds)
                  AND ff.finance_date >= today() - 60
                  AND ff.finance_date < today() - 30
            ) prev
            SETTINGS final = 1
            """;

    public ClickHouseKpiRow findKpi(List<Long> connectionIds) {
        if (connectionIds == null || connectionIds.isEmpty()) {
            return new ClickHouseKpiRow(0, null, null);
        }

        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        return ch.queryForObject(KPI_SQL, params, this::mapKpi);
    }

    private ClickHouseKpiRow mapKpi(ResultSet rs, int rowNum) throws SQLException {
        return new ClickHouseKpiRow(
                rs.getLong("critical_stock_count"),
                rs.getBigDecimal("revenue_30d_total"),
                rs.getBigDecimal("revenue_30d_trend"));
    }

    private ClickHouseEnrichment mapEnrichment(ResultSet rs, int rowNum) throws SQLException {
        return ClickHouseEnrichment.builder()
                .offerId(rs.getLong("offer_id"))
                .daysOfCover(rs.getBigDecimal("days_of_cover"))
                .stockRisk(rs.getString("stock_out_risk"))
                .revenue30d(rs.getBigDecimal("revenue_30d"))
                .netPnl30d(rs.getBigDecimal("net_pnl_30d"))
                .velocity14d(rs.getBigDecimal("velocity_14d"))
                .returnRatePct(rs.getBigDecimal("return_rate_pct"))
                .build();
    }
}
