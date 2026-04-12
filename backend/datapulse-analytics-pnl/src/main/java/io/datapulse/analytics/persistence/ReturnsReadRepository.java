package io.datapulse.analytics.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.datapulse.analytics.api.ProductReturnResponse;
import io.datapulse.analytics.api.ReturnsFilter;
import io.datapulse.analytics.api.ReturnsTrendResponse;
import io.datapulse.analytics.api.TrendGranularity;
import io.datapulse.analytics.config.ClickHouseReadJdbc;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReturnsReadRepository {

  private final ClickHouseReadJdbc jdbc;

  private static final String SETTINGS_FINAL = "\nSETTINGS final = 1";

  private static final Map<String, String> SORT_WHITELIST = Map.of(
      "returnRatePct", "return_rate_pct",
      "returnQuantity", "return_quantity",
      "returnAmount", "return_amount",
      "returnCount", "return_count",
      "saleQuantity", "sale_quantity",
      "sellerSkuId", "m.seller_sku_id",
      "distinctReasonCount", "distinct_reason_count"
  );

  private static final Set<String> SORT_DIRECTIONS = Set.of("ASC", "DESC");

  private static final String SUMMARY_SQL = """
      SELECT
          sum(return_count) AS total_return_count,
          sum(return_quantity) AS total_return_quantity,
          sum(return_amount) AS return_amount,
          sum(sale_count) AS sale_count,
          sum(sale_quantity) AS total_sale_quantity,
          if(total_sale_quantity > 0,
             total_return_quantity / total_sale_quantity * 100, NULL) AS return_rate_pct,
          topK(1)(top_return_reason)[1] AS top_return_reason,
          countDistinctIf(coalesce(seller_sku_id, product_id),
              coalesce(seller_sku_id, product_id) > 0) AS products_with_returns
      FROM mart_returns_analysis
      WHERE workspace_id = :workspaceId
      """;

  private static final String RETURN_RATE_SQL = """
      SELECT
          sum(return_quantity) AS total_return_quantity,
          sum(sale_quantity) AS total_sale_quantity,
          if(total_sale_quantity > 0,
             total_return_quantity / total_sale_quantity * 100, NULL) AS return_rate_pct
      FROM mart_returns_analysis
      WHERE workspace_id = :workspaceId
        AND period = :period
      """;

  private static final String REASON_BREAKDOWN_BASE = """
      SELECT
          return_reason,
          count() AS cnt,
          sum(ifNull(return_amount, toDecimal64(0, 2))) AS reason_amount,
          uniqExact(coalesce(seller_sku_id, product_id)) AS product_count
      FROM fact_returns
      WHERE workspace_id = :workspaceId
        AND toYYYYMM(return_date) = :period
      """;

  private static final String REASONS_BASE = """
      SELECT
          return_reason,
          count() AS cnt,
          sum(quantity) AS total_quantity,
          sum(ifNull(return_amount, toDecimal64(0, 2))) AS reason_amount,
          uniqExact(coalesce(seller_sku_id, product_id)) AS product_count
      FROM fact_returns
      WHERE workspace_id = :workspaceId
        AND toYYYYMM(return_date) = :period
      """;

  private static final String BY_PRODUCT_SQL = """
      SELECT
          m.source_platform AS source_platform,
          m.product_id AS product_id,
          m.seller_sku_id AS seller_sku_id,
          coalesce(
              nullIf(p_by_id.sku_code, ''),
              nullIf(p_by_sku.sku_code, ''),
              nullIf(p_by_id.marketplace_sku, ''),
              nullIf(p_by_sku.marketplace_sku, '')
          ) AS sku_code,
          coalesce(
              nullIf(p_by_id.product_name, ''),
              nullIf(p_by_sku.product_name, '')
          ) AS product_name,
          m.period AS period,
          m.return_count AS return_count,
          m.return_quantity AS return_quantity,
          m.return_amount AS return_amount,
          m.sale_count AS sale_count,
          m.sale_quantity AS sale_quantity,
          m.return_rate_pct AS return_rate_pct,
          m.top_return_reason AS top_return_reason,
          m.distinct_reason_count AS distinct_reason_count
      FROM mart_returns_analysis AS m
      LEFT JOIN dim_product AS p_by_id
          ON m.product_id = p_by_id.product_id
          AND m.workspace_id = p_by_id.workspace_id
      LEFT JOIN (
          SELECT workspace_id,
                 seller_sku_id,
                 anyLast(sku_code) AS sku_code,
                 anyLast(marketplace_sku) AS marketplace_sku,
                 anyLast(product_name) AS product_name
          FROM dim_product
          GROUP BY workspace_id, seller_sku_id
      ) AS p_by_sku
          ON m.seller_sku_id = p_by_sku.seller_sku_id
          AND m.workspace_id = p_by_sku.workspace_id
      WHERE m.workspace_id = :workspaceId
      """;

  private static final String TREND_SQL = """
      SELECT
          period_label,
          sum(return_quantity) AS total_return_quantity,
          sum(sale_quantity) AS total_sale_quantity,
          if(total_sale_quantity > 0,
             toDecimal64(total_return_quantity, 2) / total_sale_quantity * 100, NULL)
              AS return_rate_pct
      FROM (
          SELECT
              %s AS period_label,
              sum(quantity) AS return_quantity,
              toInt64(0) AS sale_quantity
          FROM fact_returns
          WHERE workspace_id = :workspaceId
          %s
          GROUP BY period_label

          UNION ALL

          SELECT
              %s AS period_label,
              toInt64(0) AS return_quantity,
              sum(quantity) AS sale_quantity
          FROM fact_sales
          WHERE workspace_id = :workspaceId
          %s
          GROUP BY period_label
      ) t
      GROUP BY period_label
      ORDER BY period_label
      """;

  public record SummaryRow(
      int totalReturnCount,
      int totalReturnQuantity,
      BigDecimal returnAmount,
      int saleCount,
      int totalSaleQuantity,
      BigDecimal returnRatePct,
      String topReturnReason,
      int productsWithReturns
  ) {}

  public record ReasonRow(
      String reason,
      int count,
      BigDecimal amount,
      int productCount
  ) {}

  public record FullReasonRow(
      String reason,
      int count,
      int totalQuantity,
      BigDecimal amount,
      int productCount
  ) {}

  public SummaryRow findSummary(long workspaceId, ReturnsFilter filter) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    var sb = new StringBuilder(SUMMARY_SQL);
    appendFilter(sb, params, filter);
    sb.append(SETTINGS_FINAL);

    return jdbc.ch().queryForObject(sb.toString(), params, (rs, rowNum) -> new SummaryRow(
        rs.getInt("total_return_count"),
        rs.getInt("total_return_quantity"),
        rs.getBigDecimal("return_amount"),
        rs.getInt("sale_count"),
        rs.getInt("total_sale_quantity"),
        rs.getBigDecimal("return_rate_pct"),
        rs.getString("top_return_reason"),
        rs.getInt("products_with_returns")
    ));
  }

  public BigDecimal findReturnRateForPeriod(long workspaceId, int period,
      ReturnsFilter filter) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId)
        .addValue("period", period);
    var sb = new StringBuilder(RETURN_RATE_SQL);
    appendPlatformFilter(sb, params, filter);
    sb.append(SETTINGS_FINAL);

    return jdbc.ch().queryForObject(
        sb.toString(),
        params,
        (rs, rowNum) -> rs.getBigDecimal("return_rate_pct"));
  }

  public List<ReasonRow> findReasonBreakdown(long workspaceId, int period,
      ReturnsFilter filter) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId)
        .addValue("period", period);
    var sb = new StringBuilder(REASON_BREAKDOWN_BASE);
    appendPlatformFilter(sb, params, filter);
    sb.append(" GROUP BY return_reason ORDER BY cnt DESC LIMIT 10");
    sb.append(SETTINGS_FINAL);

    return jdbc.ch().query(sb.toString(), params,
        (rs, rowNum) -> new ReasonRow(
            rs.getString("return_reason"),
            rs.getInt("cnt"),
            rs.getBigDecimal("reason_amount"),
            rs.getInt("product_count")));
  }

  public List<FullReasonRow> findReasons(long workspaceId, int period,
      ReturnsFilter filter) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId)
        .addValue("period", period);
    var sb = new StringBuilder(REASONS_BASE);
    appendPlatformFilter(sb, params, filter);
    sb.append(" GROUP BY return_reason ORDER BY cnt DESC");
    sb.append(SETTINGS_FINAL);

    return jdbc.ch().query(sb.toString(), params,
        (rs, rowNum) -> new FullReasonRow(
            rs.getString("return_reason"),
            rs.getInt("cnt"),
            rs.getInt("total_quantity"),
            rs.getBigDecimal("reason_amount"),
            rs.getInt("product_count")));
  }

  public List<ProductReturnResponse> findByProduct(long workspaceId, ReturnsFilter filter,
      String sortColumn, String sortDirection, int limit, long offset) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    var sb = new StringBuilder(BY_PRODUCT_SQL);
    appendFilter(sb, params, filter);
    appendSearchFilter(sb, params, filter);

    String orderBy = SORT_WHITELIST.getOrDefault(sortColumn, "return_rate_pct");
    String dir = SORT_DIRECTIONS.contains(sortDirection.toUpperCase())
        ? sortDirection.toUpperCase() : "DESC";
    sb.append(" ORDER BY ").append(orderBy).append(" ").append(dir).append(" NULLS LAST");
    sb.append(" LIMIT :limit OFFSET :offset");
    params.addValue("limit", limit);
    params.addValue("offset", offset);
    sb.append(SETTINGS_FINAL);

    return jdbc.ch().query(sb.toString(), params, this::mapProductReturn);
  }

  public long countByProduct(long workspaceId, ReturnsFilter filter) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    var sb = new StringBuilder("""
        SELECT count(*) FROM mart_returns_analysis AS m
        LEFT JOIN dim_product AS p_by_id
            ON m.product_id = p_by_id.product_id
            AND m.workspace_id = p_by_id.workspace_id
        LEFT JOIN (
            SELECT workspace_id,
                   seller_sku_id,
                   anyLast(sku_code) AS sku_code,
                   anyLast(marketplace_sku) AS marketplace_sku,
                   anyLast(product_name) AS product_name
            FROM dim_product
            GROUP BY workspace_id, seller_sku_id
        ) AS p_by_sku
            ON m.seller_sku_id = p_by_sku.seller_sku_id
            AND m.workspace_id = p_by_sku.workspace_id
        WHERE m.workspace_id = :workspaceId
        """);
    appendFilter(sb, params, filter);
    appendSearchFilter(sb, params, filter);
    sb.append(SETTINGS_FINAL);

    Long result = jdbc.ch().queryForObject(sb.toString(), params, Long.class);
    return result != null ? result : 0L;
  }

  public List<ReturnsTrendResponse> findTrend(long workspaceId, ReturnsFilter filter,
      TrendGranularity granularity) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    String periodExprReturns = periodExpr(granularity, "return_date");
    String periodExprSales = periodExpr(granularity, "sale_date");
    var sb = new StringBuilder(
        TREND_SQL.formatted(
            periodExprReturns, buildDateFilter("return_date", filter, params),
            periodExprSales, buildDateFilter("sale_date", filter, params)));
    sb.append(SETTINGS_FINAL);

    return jdbc.ch().query(sb.toString(), params, (rs, rowNum) -> new ReturnsTrendResponse(
        rs.getString("period_label"),
        rs.getInt("total_return_quantity"),
        rs.getInt("total_sale_quantity"),
        rs.getBigDecimal("return_rate_pct")
    ));
  }

  private void appendFilter(StringBuilder sb, MapSqlParameterSource params,
      ReturnsFilter filter) {
    Integer periodInt = filter.periodAsInt();
    if (periodInt != null) {
      sb.append(" AND period = :period");
      params.addValue("period", periodInt);
    }
    appendPlatformFilter(sb, params, filter);
  }

  private void appendPlatformFilter(StringBuilder sb, MapSqlParameterSource params,
      ReturnsFilter filter) {
    if (filter.sourcePlatform() != null && !filter.sourcePlatform().isBlank()) {
      sb.append(" AND source_platform = :sourcePlatform");
      params.addValue("sourcePlatform", filter.sourcePlatform().trim());
    }
  }

  private void appendSearchFilter(StringBuilder sb, MapSqlParameterSource params,
      ReturnsFilter filter) {
    if (filter.search() != null && !filter.search().isBlank()) {
      sb.append("""
           AND (coalesce(p_by_id.product_name, p_by_sku.product_name) ILIKE :search\
           OR coalesce(p_by_id.sku_code, p_by_sku.sku_code) ILIKE :search)""");
      params.addValue("search", "%%" + filter.search().trim() + "%%");
    }
  }

  private String periodExpr(TrendGranularity granularity, String dateColumn) {
    return switch (granularity) {
      case DAILY -> "toString(%s)".formatted(dateColumn);
      case WEEKLY -> "toString(toStartOfWeek(%s))".formatted(dateColumn);
      case MONTHLY -> "toString(toYYYYMM(%s))".formatted(dateColumn);
    };
  }

  private String buildDateFilter(String column, ReturnsFilter filter,
      MapSqlParameterSource params) {
    StringBuilder sb = new StringBuilder();
    LocalDate from = filter.from();
    LocalDate to = filter.to();
    if (from != null) {
      sb.append(" AND ").append(column).append(" >= :dateFrom");
      params.addValue("dateFrom", from);
    }
    if (to != null) {
      sb.append(" AND ").append(column).append(" <= :dateTo");
      params.addValue("dateTo", to);
    }
    if (filter.sourcePlatform() != null && !filter.sourcePlatform().isBlank()) {
      sb.append(" AND source_platform = :sourcePlatform");
      params.addValue("sourcePlatform", filter.sourcePlatform().trim());
    }
    return sb.toString();
  }

  private ProductReturnResponse mapProductReturn(ResultSet rs, int rowNum) throws SQLException {
    return new ProductReturnResponse(
        rs.getString("source_platform"),
        rs.getLong("product_id"),
        rs.getLong("seller_sku_id"),
        rs.getString("sku_code"),
        rs.getString("product_name"),
        rs.getInt("period"),
        rs.getInt("return_count"),
        rs.getInt("return_quantity"),
        rs.getBigDecimal("return_amount"),
        rs.getInt("sale_count"),
        rs.getInt("sale_quantity"),
        rs.getBigDecimal("return_rate_pct"),
        rs.getString("top_return_reason"),
        rs.getInt("distinct_reason_count")
    );
  }
}
