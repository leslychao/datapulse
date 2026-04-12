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
import java.util.Set;
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
                ret.return_rate_pct,
                ad.ad_spend_30d,
                ad.drr_30d_pct,
                ad.ad_cpo,
                ad.ad_roas
            FROM (
                SELECT
                    product_id,
                    minOrNull(days_of_cover) AS days_of_cover,
                    multiIf(
                        countIf(stock_out_risk = 'CRITICAL') > 0, 'CRITICAL',
                        countIf(stock_out_risk = 'WARNING') > 0, 'WARNING',
                        'NORMAL'
                    ) AS stock_out_risk
                FROM (
                    SELECT
                        workspace_id,
                        product_id,
                        warehouse_id,
                        argMax(days_of_cover, ver) AS days_of_cover,
                        argMax(stock_out_risk, ver) AS stock_out_risk
                    FROM mart_inventory_analysis FINAL
                    WHERE product_id IN (:offerIds)
                    GROUP BY workspace_id, product_id, warehouse_id
                ) AS mart_slice
                GROUP BY product_id
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
                    AND ff.workspace_id = dp.workspace_id
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
            LEFT JOIN (
                SELECT
                    dp.product_id,
                    map.spend       AS ad_spend_30d,
                    map.drr_pct     AS drr_30d_pct,
                    map.cpo         AS ad_cpo,
                    map.roas        AS ad_roas
                FROM mart_advertising_product AS map
                INNER JOIN dim_product AS dp
                    ON map.marketplace_sku = dp.marketplace_sku
                WHERE dp.product_id IN (:offerIds)
                  AND map.period = (
                      SELECT max(period) FROM mart_advertising_product
                  )
            ) ad ON inv.product_id = ad.product_id
            SETTINGS final = 1
            """;

    private static final Set<String> CH_SORT_COLUMNS = Set.of(
            "revenue30d", "netPnl30d", "velocity14d",
            "returnRatePct", "daysOfCover", "stockRisk"
    );

    private static final Map<String, String> CH_SORT_COLUMN_MAP = Map.of(
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

    public List<Long> findSortedOfferIds(long workspaceId, String sortColumn,
                                          String direction, int limit) {
        String chColumn = CH_SORT_COLUMN_MAP.get(sortColumn);
        if (chColumn == null) {
            return List.of();
        }

        String sql = """
                SELECT inv.product_id AS offer_id
                FROM (
                    SELECT
                        product_id,
                        minOrNull(days_of_cover) AS days_of_cover,
                        multiIf(
                            countIf(stock_out_risk = 'CRITICAL') > 0, 'CRITICAL',
                            countIf(stock_out_risk = 'WARNING') > 0, 'WARNING',
                            'NORMAL'
                        ) AS stock_out_risk
                    FROM (
                        SELECT
                            workspace_id,
                            product_id,
                            warehouse_id,
                            argMax(days_of_cover, ver) AS days_of_cover,
                            argMax(stock_out_risk, ver) AS stock_out_risk
                        FROM mart_inventory_analysis FINAL
                        WHERE workspace_id = :workspaceId
                        GROUP BY workspace_id, product_id, warehouse_id
                    ) AS mart_slice
                    GROUP BY product_id
                ) inv
                LEFT JOIN (
                    SELECT dp.product_id,
                           sum(ff.revenue_amount) AS revenue_30d,
                           sumIf(ff.net_payout,
                                 ff.attribution_level IN ('POSTING','PRODUCT')) AS net_pnl_30d
                    FROM fact_finance ff
                    JOIN dim_product dp ON ff.seller_sku_id = dp.seller_sku_id
                        AND ff.workspace_id = dp.workspace_id
                    WHERE ff.workspace_id = :workspaceId
                      AND ff.finance_date >= today() - 30
                    GROUP BY dp.product_id
                ) fin ON inv.product_id = fin.product_id
                LEFT JOIN (
                    SELECT product_id, toDecimal64(sum(quantity),2)/14 AS velocity_14d
                    FROM fact_sales
                    WHERE workspace_id = :workspaceId AND sale_date >= today()-14
                    GROUP BY product_id
                ) sales ON inv.product_id = sales.product_id
                LEFT JOIN (
                    SELECT product_id, return_rate_pct
                    FROM mart_returns_analysis
                    WHERE workspace_id = :workspaceId
                      AND period = (SELECT max(period) FROM mart_returns_analysis
                                    WHERE workspace_id = :workspaceId)
                ) ret ON inv.product_id = ret.product_id
                ORDER BY %s %s NULLS LAST
                LIMIT :limit
                SETTINGS final = 1
                """.formatted(chColumn, direction);

        var params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("limit", limit);

        return ch.queryForList(sql, params, Long.class);
    }

    public List<Long> findOfferIdsByStockRisk(long workspaceId, String stockRisk) {
        String sql = """
                SELECT DISTINCT m.product_id
                FROM (
                    SELECT
                        workspace_id,
                        product_id,
                        warehouse_id,
                        argMax(stock_out_risk, ver) AS stock_out_risk
                    FROM mart_inventory_analysis FINAL
                    WHERE workspace_id = :workspaceId
                    GROUP BY workspace_id, product_id, warehouse_id
                ) AS m
                WHERE m.stock_out_risk = :stockRisk
                LIMIT 10000
                SETTINGS final = 1
                """;

        var params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
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
                if(prev.revenue_prev_30d > 0,
                   round((cur.revenue_30d_total - prev.revenue_prev_30d)
                         / prev.revenue_prev_30d * 100, 2),
                   NULL) AS revenue_30d_trend
            FROM (
                SELECT countDistinct(m.product_id) AS critical_stock_count
                FROM (
                    SELECT
                        workspace_id,
                        product_id,
                        warehouse_id,
                        argMax(stock_out_risk, ver) AS stock_out_risk
                    FROM mart_inventory_analysis FINAL
                    WHERE workspace_id = :workspaceId
                    GROUP BY workspace_id, product_id, warehouse_id
                ) AS m
                WHERE m.stock_out_risk = 'CRITICAL'
            ) inv
            CROSS JOIN (
                SELECT coalesce(sum(ff.revenue_amount), 0) AS revenue_30d_total
                FROM fact_finance AS ff
                WHERE ff.workspace_id = :workspaceId
                  AND ff.finance_date >= today() - 30
            ) cur
            CROSS JOIN (
                SELECT coalesce(sum(ff.revenue_amount), 0) AS revenue_prev_30d
                FROM fact_finance AS ff
                WHERE ff.workspace_id = :workspaceId
                  AND ff.finance_date >= today() - 60
                  AND ff.finance_date < today() - 30
            ) prev
            SETTINGS final = 1
            """;

    public ClickHouseKpiRow findKpi(long workspaceId) {
        var params = new MapSqlParameterSource("workspaceId", workspaceId);
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
                .adSpend30d(rs.getBigDecimal("ad_spend_30d"))
                .drr30dPct(rs.getBigDecimal("drr_30d_pct"))
                .adCpo(rs.getBigDecimal("ad_cpo"))
                .adRoas(rs.getBigDecimal("ad_roas"))
                .build();
    }

    private static final String STOCK_SNAPSHOT_SQL = """
            SELECT
                product_id AS offer_id,
                toInt32(sum(available)) AS snapshot_stock
            FROM (
                SELECT
                    workspace_id,
                    product_id,
                    warehouse_id,
                    argMax(available, ver) AS available
                FROM fact_inventory_snapshot FINAL
                WHERE workspace_id = :workspaceId
                GROUP BY workspace_id, product_id, warehouse_id
            ) AS latest_rows
            GROUP BY product_id
            SETTINGS final = 1
            """;

    public Map<Long, Integer> findLatestSnapshotStocks(long workspaceId) {
        var params = new MapSqlParameterSource("workspaceId", workspaceId);
        return ch.query(STOCK_SNAPSHOT_SQL, params, (ResultSet rs) -> {
            Map<Long, Integer> result = new java.util.HashMap<>();
            while (rs.next()) {
                result.put(rs.getLong("offer_id"), rs.getInt("snapshot_stock"));
            }
            return result;
        });
    }
}
