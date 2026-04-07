package io.datapulse.analytics.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.api.InventoryFilter;
import io.datapulse.analytics.api.InventoryOverviewResponse;
import io.datapulse.analytics.api.ProductInventoryResponse;
import io.datapulse.analytics.api.StockHistoryResponse;
import io.datapulse.analytics.config.ClickHouseReadJdbc;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InventoryReadRepository {

    private final ClickHouseReadJdbc jdbc;

    private static final String SETTINGS_FINAL = "\nSETTINGS final = 1";

    private static final Map<String, String> SORT_WHITELIST = Map.of(
            "daysOfCover", "days_of_cover",
            "available", "available",
            "frozenCapital", "frozen_capital",
            "stockOutRisk", "stock_out_risk",
            "avgDailySales14d", "avg_daily_sales_14d",
            "productName", "p.product_name",
            "skuCode", "p.sku_code"
    );

    private static final String OVERVIEW_SQL = """
            SELECT
                count(DISTINCT m.product_id) AS total_skus,
                countIf(m.stock_out_risk = 'CRITICAL') AS critical_risk_count,
                countIf(m.stock_out_risk = 'WARNING') AS warning_risk_count,
                countIf(m.stock_out_risk = 'NORMAL') AS normal_risk_count,
                sum(m.frozen_capital) AS total_frozen_capital
            FROM mart_inventory_analysis AS m
            WHERE m.connection_id IN (:connectionIds)
              AND m.analysis_date = (
                  SELECT max(analysis_date) FROM mart_inventory_analysis
                  WHERE connection_id IN (:connectionIds)
              )
            """;

    private static final String BY_PRODUCT_SQL = """
            SELECT
                m.connection_id AS connection_id,
                m.source_platform AS source_platform,
                m.product_id AS product_id,
                m.seller_sku_id AS seller_sku_id,
                p.sku_code AS sku_code,
                p.product_name AS product_name,
                m.warehouse_id AS warehouse_id,
                w.name AS warehouse_name,
                m.analysis_date AS analysis_date,
                m.available AS available,
                m.reserved AS reserved,
                m.avg_daily_sales_14d AS avg_daily_sales_14d,
                m.days_of_cover AS days_of_cover,
                m.stock_out_risk AS stock_out_risk,
                m.cost_price AS cost_price,
                m.frozen_capital AS frozen_capital,
                m.recommended_replenishment AS recommended_replenishment
            FROM mart_inventory_analysis AS m
            LEFT JOIN dim_product AS p ON m.product_id = p.product_id
            LEFT JOIN dim_warehouse AS w ON m.warehouse_id = w.warehouse_id
            WHERE m.connection_id IN (:connectionIds)
              AND m.analysis_date = (
                  SELECT max(analysis_date) FROM mart_inventory_analysis
                  WHERE connection_id IN (:connectionIds)
              )
            """;

    private static final String STOCK_HISTORY_SQL = """
            SELECT
                toDate(captured_at) AS date,
                available,
                reserved,
                warehouse_id,
                w.name AS warehouse_name
            FROM fact_inventory_snapshot AS s
            LEFT JOIN dim_warehouse AS w ON s.warehouse_id = w.warehouse_id
            WHERE s.product_id = :productId
              AND s.connection_id IN (:connectionIds)
              AND s.captured_date >= :dateFrom
              AND s.captured_date <= :dateTo
            ORDER BY date, warehouse_id
            SETTINGS final = 1
            """;

    public InventoryOverviewResponse findOverview(List<Long> connectionIds, InventoryFilter filter) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        var sb = new StringBuilder(OVERVIEW_SQL);
        appendOverviewFilter(sb, params, filter);
        sb.append(SETTINGS_FINAL);

        return jdbc.ch().queryForObject(sb.toString(), params, (rs, rowNum) -> new InventoryOverviewResponse(
                rs.getInt("total_skus"),
                rs.getInt("critical_risk_count"),
                rs.getInt("warning_risk_count"),
                rs.getInt("normal_risk_count"),
                rs.getBigDecimal("total_frozen_capital")
        ));
    }

    public List<ProductInventoryResponse> findByProduct(List<Long> connectionIds, InventoryFilter filter,
                                                         String sortColumn, int limit, long offset) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        var sb = new StringBuilder(BY_PRODUCT_SQL);
        appendProductFilter(sb, params, filter);

        String orderBy = SORT_WHITELIST.getOrDefault(sortColumn, "days_of_cover");
        sb.append(" ORDER BY ").append(orderBy).append(" ASC NULLS LAST");
        sb.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", limit);
        params.addValue("offset", offset);
        sb.append(SETTINGS_FINAL);

        return jdbc.ch().query(sb.toString(), params, this::mapProductInventory);
    }

    public long countByProduct(List<Long> connectionIds, InventoryFilter filter) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        var sb = new StringBuilder("""
                SELECT count(*) FROM mart_inventory_analysis AS m
                LEFT JOIN dim_product AS p ON m.product_id = p.product_id
                WHERE m.connection_id IN (:connectionIds)
                  AND m.analysis_date = (
                      SELECT max(analysis_date) FROM mart_inventory_analysis
                      WHERE connection_id IN (:connectionIds)
                  )
                """);
        appendProductFilter(sb, params, filter);
        sb.append(SETTINGS_FINAL);

        Long result = jdbc.ch().queryForObject(sb.toString(), params, Long.class);
        return result != null ? result : 0L;
    }

    public List<StockHistoryResponse> findStockHistory(List<Long> connectionIds, long productId,
                                                        LocalDate from, LocalDate to) {
        var params = new MapSqlParameterSource()
                .addValue("connectionIds", connectionIds)
                .addValue("productId", productId)
                .addValue("dateFrom", from)
                .addValue("dateTo", to);

        return jdbc.ch().query(STOCK_HISTORY_SQL, params, (rs, rowNum) -> new StockHistoryResponse(
                rs.getDate("date").toLocalDate(),
                rs.getInt("available"),
                getBoxedInt(rs, "reserved"),
                getBoxedInt(rs, "warehouse_id"),
                rs.getString("warehouse_name")
        ));
    }

    private void appendOverviewFilter(StringBuilder sb, MapSqlParameterSource params, InventoryFilter filter) {
    }

    private void appendProductFilter(StringBuilder sb, MapSqlParameterSource params, InventoryFilter filter) {
        if (filter.stockOutRisk() != null && !filter.stockOutRisk().isBlank()) {
            sb.append(" AND m.stock_out_risk = :stockOutRisk");
            params.addValue("stockOutRisk", filter.stockOutRisk().trim().toUpperCase());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            sb.append(" AND (p.product_name ILIKE :search OR p.sku_code ILIKE :search)");
            params.addValue("search", "%%" + filter.search().trim() + "%%");
        }
    }

    private ProductInventoryResponse mapProductInventory(ResultSet rs, int rowNum) throws SQLException {
        return new ProductInventoryResponse(
                rs.getLong("connection_id"),
                rs.getString("source_platform"),
                rs.getLong("product_id"),
                rs.getLong("seller_sku_id"),
                rs.getString("sku_code"),
                rs.getString("product_name"),
                getBoxedInt(rs, "warehouse_id"),
                rs.getString("warehouse_name"),
                rs.getDate("analysis_date").toLocalDate(),
                rs.getInt("available"),
                getBoxedInt(rs, "reserved"),
                rs.getBigDecimal("avg_daily_sales_14d"),
                rs.getBigDecimal("days_of_cover"),
                rs.getString("stock_out_risk"),
                rs.getBigDecimal("cost_price"),
                rs.getBigDecimal("frozen_capital"),
                getBoxedInt(rs, "recommended_replenishment")
        );
    }

    private Integer getBoxedInt(ResultSet rs, String column) throws SQLException {
        int val = rs.getInt(column);
        return rs.wasNull() ? null : val;
    }
}
