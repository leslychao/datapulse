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

  /**
   * One logical row per offer × warehouse: {@code ReplacingMergeTree} ORDER BY includes
   * {@code analysis_date}, so several physical rows can coexist; we take the row with highest
   * {@code ver} (materialization batch). Using {@code GROUP BY} + {@code argMax(col, ver)} is the
   * standard ClickHouse pattern — single scan, all measures from the winning row; avoids
   * self-join and global {@code max(analysis_date)} which hid most SKUs.
   */
  private static final String MART_INVENTORY_LATEST = """
      SELECT
          workspace_id,
          product_id,
          warehouse_id,
          argMax(connection_id, ver) AS connection_id,
          argMax(source_platform, ver) AS source_platform,
          argMax(seller_sku_id, ver) AS seller_sku_id,
          argMax(analysis_date, ver) AS analysis_date,
          argMax(available, ver) AS available,
          argMax(reserved, ver) AS reserved,
          argMax(avg_daily_sales_14d, ver) AS avg_daily_sales_14d,
          argMax(days_of_cover, ver) AS days_of_cover,
          argMax(stock_out_risk, ver) AS stock_out_risk,
          argMax(cost_price, ver) AS cost_price,
          argMax(frozen_capital, ver) AS frozen_capital,
          argMax(recommended_replenishment, ver) AS recommended_replenishment
      FROM mart_inventory_analysis FINAL
      WHERE workspace_id = :workspaceId
      GROUP BY workspace_id, product_id, warehouse_id
      """;

  private static final String OVERVIEW_SQL = """
      SELECT
          count(DISTINCT m.product_id) AS total_skus,
          countIf(m.stock_out_risk = 'CRITICAL') AS critical_count,
          countIf(m.stock_out_risk = 'WARNING') AS warning_count,
          countIf(m.stock_out_risk = 'NORMAL') AS normal_count,
          sum(m.frozen_capital) AS frozen_capital
      FROM (
      """ + MART_INVENTORY_LATEST + """
      ) AS m
      """;

  private static final String TOP_CRITICAL_SQL = """
      SELECT
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
      FROM (
      """ + MART_INVENTORY_LATEST + """
      ) AS m
      LEFT JOIN dim_product AS p
          ON m.product_id = p.product_id AND m.connection_id = p.connection_id
      LEFT JOIN dim_warehouse AS w ON m.warehouse_id = w.warehouse_id
      WHERE m.stock_out_risk = 'CRITICAL'
      ORDER BY m.days_of_cover ASC NULLS FIRST, m.available ASC
      LIMIT 10
      """;

  private static final String BY_PRODUCT_SQL = """
      SELECT
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
      FROM (
      """ + MART_INVENTORY_LATEST + """
      ) AS m
      LEFT JOIN dim_product AS p
          ON m.product_id = p.product_id AND m.connection_id = p.connection_id
      LEFT JOIN dim_warehouse AS w ON m.warehouse_id = w.warehouse_id
      WHERE 1 = 1
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
        AND s.workspace_id = :workspaceId
        AND s.captured_date >= :dateFrom
        AND s.captured_date <= :dateTo
      ORDER BY date, warehouse_id
      SETTINGS final = 1
      """;

  private static final String AGGREGATE_STOCK_HISTORY_SQL = """
      SELECT
          toDate(captured_at) AS date,
          sum(available) AS available,
          sum(reserved) AS reserved
      FROM fact_inventory_snapshot
      WHERE workspace_id = :workspaceId
        AND captured_date >= :dateFrom
        AND captured_date <= :dateTo
      GROUP BY date
      ORDER BY date
      SETTINGS final = 1
      """;

  public InventoryOverviewResponse findOverview(
      long workspaceId, InventoryFilter filter) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    var sb = new StringBuilder(OVERVIEW_SQL);
    sb.append(SETTINGS_FINAL);

    return jdbc.ch().queryForObject(sb.toString(), params, (rs, rowNum) ->
        new InventoryOverviewResponse(
            rs.getInt("total_skus"),
            rs.getInt("critical_count"),
            rs.getInt("warning_count"),
            rs.getInt("normal_count"),
            rs.getBigDecimal("frozen_capital"),
            List.of()
        ));
  }

  public List<ProductInventoryResponse> findTopCritical(long workspaceId) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    var sb = new StringBuilder(TOP_CRITICAL_SQL);
    sb.append(SETTINGS_FINAL);
    return jdbc.ch().query(sb.toString(), params, this::mapProductInventory);
  }

  public List<ProductInventoryResponse> findByProduct(
      long workspaceId, InventoryFilter filter,
      String sortColumn, String sortDirection,
      int limit, long offset) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    var sb = new StringBuilder(BY_PRODUCT_SQL);
    appendProductFilter(sb, params, filter);

    String orderBy = SORT_WHITELIST.getOrDefault(sortColumn, "days_of_cover");
    String dir = "DESC".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
    sb.append(" ORDER BY ").append(orderBy).append(" ").append(dir).append(" NULLS LAST");
    sb.append(" LIMIT :limit OFFSET :offset");
    params.addValue("limit", limit);
    params.addValue("offset", offset);
    sb.append(SETTINGS_FINAL);

    return jdbc.ch().query(sb.toString(), params, this::mapProductInventory);
  }

  public long countByProduct(long workspaceId, InventoryFilter filter) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    var sb = new StringBuilder("""
        SELECT count(*) FROM (
        """ + MART_INVENTORY_LATEST + """
        ) AS m
        LEFT JOIN dim_product AS p
            ON m.product_id = p.product_id AND m.connection_id = p.connection_id
        WHERE 1 = 1
        """);
    appendProductFilter(sb, params, filter);
    sb.append(SETTINGS_FINAL);

    Long result = jdbc.ch().queryForObject(sb.toString(), params, Long.class);
    return result != null ? result : 0L;
  }

  public List<StockHistoryResponse> findStockHistory(
      long workspaceId, long productId,
      LocalDate from, LocalDate to) {
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("productId", productId)
        .addValue("dateFrom", from)
        .addValue("dateTo", to);

    return jdbc.ch().query(STOCK_HISTORY_SQL, params, (rs, rowNum) ->
        new StockHistoryResponse(
            rs.getDate("date").toLocalDate(),
            rs.getInt("available"),
            getBoxedInt(rs, "reserved"),
            getBoxedInt(rs, "warehouse_id"),
            rs.getString("warehouse_name")
        ));
  }

  public List<StockHistoryResponse> findAggregateStockHistory(
      long workspaceId, LocalDate from, LocalDate to) {
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("dateFrom", from)
        .addValue("dateTo", to);

    return jdbc.ch().query(AGGREGATE_STOCK_HISTORY_SQL, params, (rs, rowNum) ->
        new StockHistoryResponse(
            rs.getDate("date").toLocalDate(),
            rs.getInt("available"),
            getBoxedInt(rs, "reserved"),
            null,
            null
        ));
  }

  private void appendProductFilter(StringBuilder sb,
      MapSqlParameterSource params, InventoryFilter filter) {
    if (filter.stockOutRisk() != null && !filter.stockOutRisk().isBlank()) {
      sb.append(" AND m.stock_out_risk = :stockOutRisk");
      params.addValue("stockOutRisk", filter.stockOutRisk().trim().toUpperCase());
    }
    if (filter.search() != null && !filter.search().isBlank()) {
      sb.append(" AND (p.product_name ILIKE :search OR p.sku_code ILIKE :search)");
      params.addValue("search", "%%" + filter.search().trim() + "%%");
    }
    if (filter.productId() != null) {
      sb.append(" AND m.product_id = :productId");
      params.addValue("productId", filter.productId());
    }
  }

  private ProductInventoryResponse mapProductInventory(ResultSet rs, int rowNum)
      throws SQLException {
    return new ProductInventoryResponse(
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
