package io.datapulse.analytics.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.api.ProductReturnResponse;
import io.datapulse.analytics.api.ReturnsFilter;
import io.datapulse.analytics.api.ReturnsSummaryResponse;
import io.datapulse.analytics.api.ReturnsTrendResponse;
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
            "financialRefundAmount", "financial_refund_amount",
            "penaltiesAmount", "penalties_amount",
            "sellerSkuId", "m.seller_sku_id"
    );

    private static final String SUMMARY_SQL = """
            SELECT
                connection_id,
                source_platform,
                sum(return_count) AS return_count,
                sum(return_quantity) AS return_quantity,
                sum(return_amount) AS return_amount,
                sum(sale_count) AS sale_count,
                sum(sale_quantity) AS sale_quantity,
                if(sum(sale_quantity) > 0,
                   sum(return_quantity) / sum(sale_quantity) * 100, NULL) AS return_rate_pct,
                sum(financial_refund_amount) AS financial_refund_amount,
                sum(penalties_amount) AS penalties_amount,
                topK(1)(top_return_reason)[1] AS top_return_reason
            FROM mart_returns_analysis
            WHERE connection_id IN (:connectionIds)
            """;

    private static final String BY_PRODUCT_SQL = """
            SELECT
                m.connection_id,
                m.source_platform,
                m.product_id,
                m.seller_sku_id,
                p.sku_code,
                p.product_name,
                m.period,
                m.return_count,
                m.return_quantity,
                m.return_amount,
                m.sale_quantity,
                m.return_rate_pct,
                m.financial_refund_amount,
                m.penalties_amount,
                m.top_return_reason
            FROM mart_returns_analysis AS m
            LEFT JOIN dim_product AS p ON m.product_id = p.product_id
            WHERE m.connection_id IN (:connectionIds)
            """;

    private static final String TREND_SQL = """
            SELECT
                period,
                sum(return_quantity) AS return_quantity,
                sum(sale_quantity) AS sale_quantity,
                if(sum(sale_quantity) > 0,
                   sum(return_quantity) / sum(sale_quantity) * 100, NULL) AS return_rate_pct,
                sum(financial_refund_amount) AS financial_refund_amount
            FROM mart_returns_analysis
            WHERE connection_id IN (:connectionIds)
            """;

    public List<ReturnsSummaryResponse> findSummary(List<Long> connectionIds, ReturnsFilter filter) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        var sb = new StringBuilder(SUMMARY_SQL);
        appendFilter(sb, params, filter);
        sb.append(" GROUP BY connection_id, source_platform");
        sb.append(SETTINGS_FINAL);

        return jdbc.ch().query(sb.toString(), params, this::mapSummary);
    }

    public List<ProductReturnResponse> findByProduct(List<Long> connectionIds, ReturnsFilter filter,
                                                      String sortColumn, int limit, long offset) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        var sb = new StringBuilder(BY_PRODUCT_SQL);
        appendFilter(sb, params, filter);
        appendSearchFilter(sb, params, filter);

        String orderBy = SORT_WHITELIST.getOrDefault(sortColumn, "return_rate_pct");
        sb.append(" ORDER BY ").append(orderBy).append(" DESC NULLS LAST");
        sb.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", limit);
        params.addValue("offset", offset);
        sb.append(SETTINGS_FINAL);

        return jdbc.ch().query(sb.toString(), params, this::mapProductReturn);
    }

    public long countByProduct(List<Long> connectionIds, ReturnsFilter filter) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        var sb = new StringBuilder("""
                SELECT count(*) FROM mart_returns_analysis AS m
                LEFT JOIN dim_product AS p ON m.product_id = p.product_id
                WHERE m.connection_id IN (:connectionIds)
                """);
        appendFilter(sb, params, filter);
        appendSearchFilter(sb, params, filter);
        sb.append(SETTINGS_FINAL);

        Long result = jdbc.ch().queryForObject(sb.toString(), params, Long.class);
        return result != null ? result : 0L;
    }

    public List<ReturnsTrendResponse> findTrend(List<Long> connectionIds, ReturnsFilter filter) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        var sb = new StringBuilder(TREND_SQL);
        appendFilter(sb, params, filter);
        sb.append(" GROUP BY period ORDER BY period");
        sb.append(SETTINGS_FINAL);

        return jdbc.ch().query(sb.toString(), params, (rs, rowNum) -> new ReturnsTrendResponse(
                rs.getInt("period"),
                rs.getInt("return_quantity"),
                rs.getInt("sale_quantity"),
                rs.getBigDecimal("return_rate_pct"),
                rs.getBigDecimal("financial_refund_amount")
        ));
    }

    private void appendFilter(StringBuilder sb, MapSqlParameterSource params, ReturnsFilter filter) {
        Integer periodInt = filter.periodAsInt();
        if (periodInt != null) {
            sb.append(" AND period = :period");
            params.addValue("period", periodInt);
        }
    }

    private void appendSearchFilter(StringBuilder sb, MapSqlParameterSource params, ReturnsFilter filter) {
        if (filter.search() != null && !filter.search().isBlank()) {
            sb.append(" AND (p.product_name ILIKE :search OR p.sku_code ILIKE :search)");
            params.addValue("search", "%%" + filter.search().trim() + "%%");
        }
    }

    private ReturnsSummaryResponse mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ReturnsSummaryResponse(
                rs.getLong("connection_id"),
                rs.getString("source_platform"),
                rs.getInt("return_count"),
                rs.getInt("return_quantity"),
                rs.getBigDecimal("return_amount"),
                rs.getInt("sale_count"),
                rs.getInt("sale_quantity"),
                rs.getBigDecimal("return_rate_pct"),
                rs.getBigDecimal("financial_refund_amount"),
                rs.getBigDecimal("penalties_amount"),
                rs.getString("top_return_reason")
        );
    }

    private ProductReturnResponse mapProductReturn(ResultSet rs, int rowNum) throws SQLException {
        return new ProductReturnResponse(
                rs.getLong("connection_id"),
                rs.getString("source_platform"),
                rs.getLong("product_id"),
                rs.getLong("seller_sku_id"),
                rs.getString("sku_code"),
                rs.getString("product_name"),
                rs.getInt("period"),
                rs.getInt("return_count"),
                rs.getInt("return_quantity"),
                rs.getBigDecimal("return_amount"),
                rs.getInt("sale_quantity"),
                rs.getBigDecimal("return_rate_pct"),
                rs.getBigDecimal("financial_refund_amount"),
                rs.getBigDecimal("penalties_amount"),
                rs.getString("top_return_reason")
        );
    }
}
