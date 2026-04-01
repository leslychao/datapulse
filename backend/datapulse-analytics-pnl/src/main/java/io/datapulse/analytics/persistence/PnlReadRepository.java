package io.datapulse.analytics.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.api.PnlFilter;
import io.datapulse.analytics.api.PnlSummaryResponse;
import io.datapulse.analytics.api.PnlTrendResponse;
import io.datapulse.analytics.api.PostingDetailResponse;
import io.datapulse.analytics.api.PostingPnlResponse;
import io.datapulse.analytics.api.ProductPnlResponse;
import io.datapulse.analytics.api.TrendGranularity;
import io.datapulse.analytics.config.ClickHouseReadJdbc;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PnlReadRepository {

    private final ClickHouseReadJdbc jdbc;

    private static final String SETTINGS_FINAL = "\nSETTINGS final = 1";

    private static final Map<String, String> PRODUCT_SORT_WHITELIST = Map.ofEntries(
            Map.entry("revenueAmount", "revenue_amount"),
            Map.entry("marketplacePnl", "marketplace_pnl"),
            Map.entry("fullPnl", "full_pnl"),
            Map.entry("netCogs", "net_cogs"),
            Map.entry("advertisingCost", "advertising_cost"),
            Map.entry("logisticsCostAmount", "logistics_cost_amount"),
            Map.entry("refundAmount", "refund_amount"),
            Map.entry("netPayout", "net_payout"),
            Map.entry("sellerSkuId", "seller_sku_id")
    );

    private static final Map<String, String> POSTING_SORT_WHITELIST = Map.ofEntries(
            Map.entry("financeDate", "finance_date"),
            Map.entry("revenueAmount", "revenue_amount"),
            Map.entry("netPayout", "net_payout"),
            Map.entry("reconciliationResidual", "reconciliation_residual"),
            Map.entry("postingId", "posting_id")
    );

    private static final String SUMMARY_SQL = """
            SELECT
                connection_id,
                source_platform,
                sum(revenue_amount) AS revenue_amount,
                sum(marketplace_commission_amount) AS marketplace_commission_amount,
                sum(acquiring_commission_amount) AS acquiring_commission_amount,
                sum(logistics_cost_amount) AS logistics_cost_amount,
                sum(storage_cost_amount) AS storage_cost_amount,
                sum(penalties_amount) AS penalties_amount,
                sum(marketing_cost_amount) AS marketing_cost_amount,
                sum(acceptance_cost_amount) AS acceptance_cost_amount,
                sum(other_marketplace_charges_amount) AS other_marketplace_charges_amount,
                sum(compensation_amount) AS compensation_amount,
                sum(refund_amount) AS refund_amount,
                sum(net_payout) AS net_payout,
                sum(gross_cogs) AS gross_cogs,
                sum(net_cogs) AS net_cogs,
                sum(advertising_cost) AS advertising_cost,
                sum(marketplace_pnl) AS marketplace_pnl,
                sum(full_pnl) AS full_pnl
            FROM mart_product_pnl
            WHERE connection_id IN (:connectionIds)
            """;

    private static final String BY_PRODUCT_SQL = """
            SELECT
                m.connection_id,
                m.source_platform,
                m.seller_sku_id,
                m.product_id,
                m.period,
                m.attribution_level,
                p.sku_code,
                p.product_name,
                m.revenue_amount,
                m.marketplace_commission_amount,
                m.acquiring_commission_amount,
                m.logistics_cost_amount,
                m.storage_cost_amount,
                m.penalties_amount,
                m.marketing_cost_amount,
                m.acceptance_cost_amount,
                m.other_marketplace_charges_amount,
                m.compensation_amount,
                m.refund_amount,
                m.net_payout,
                m.gross_cogs,
                m.net_cogs,
                m.cogs_status,
                m.advertising_cost,
                m.marketplace_pnl,
                m.full_pnl
            FROM mart_product_pnl AS m
            LEFT JOIN dim_product AS p ON m.product_id = p.product_id
            WHERE m.connection_id IN (:connectionIds)
            """;

    private static final String BY_POSTING_SQL = """
            SELECT
                posting_id,
                connection_id,
                source_platform,
                order_id,
                seller_sku_id,
                product_id,
                finance_date,
                revenue_amount,
                marketplace_commission_amount,
                acquiring_commission_amount,
                logistics_cost_amount,
                storage_cost_amount,
                penalties_amount,
                marketing_cost_amount,
                acceptance_cost_amount,
                other_marketplace_charges_amount,
                compensation_amount,
                refund_amount,
                net_payout,
                quantity,
                gross_cogs,
                net_cogs,
                cogs_status,
                reconciliation_residual
            FROM mart_posting_pnl
            WHERE connection_id IN (:connectionIds)
            """;

    private static final String POSTING_DETAIL_SQL = """
            SELECT
                entry_id,
                entry_type,
                attribution_level,
                finance_date,
                revenue_amount,
                marketplace_commission_amount,
                acquiring_commission_amount,
                logistics_cost_amount,
                storage_cost_amount,
                penalties_amount,
                marketing_cost_amount,
                acceptance_cost_amount,
                other_marketplace_charges_amount,
                compensation_amount,
                refund_amount,
                net_payout
            FROM fact_finance
            WHERE posting_id = :postingId
              AND connection_id IN (:connectionIds)
            ORDER BY finance_date, entry_id
            SETTINGS final = 1
            """;

    public List<PnlSummaryResponse> findSummary(List<Long> connectionIds, PnlFilter filter) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        var sb = new StringBuilder(SUMMARY_SQL);
        appendPeriodFilter(sb, params, filter);
        sb.append(" GROUP BY connection_id, source_platform");
        sb.append(SETTINGS_FINAL);

        return jdbc.ch().query(sb.toString(), params, this::mapSummary);
    }

    public List<ProductPnlResponse> findByProduct(List<Long> connectionIds, PnlFilter filter,
                                                   String sortColumn, int limit, long offset) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        var sb = new StringBuilder(BY_PRODUCT_SQL);
        appendProductFilter(sb, params, filter);

        String orderBy = PRODUCT_SORT_WHITELIST.getOrDefault(sortColumn, "revenue_amount");
        sb.append(" ORDER BY ").append(orderBy).append(" DESC NULLS LAST");
        sb.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", limit);
        params.addValue("offset", offset);
        sb.append(SETTINGS_FINAL);

        return jdbc.ch().query(sb.toString(), params, this::mapProductPnl);
    }

    public long countByProduct(List<Long> connectionIds, PnlFilter filter) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        var sb = new StringBuilder("SELECT count(*) FROM mart_product_pnl AS m WHERE m.connection_id IN (:connectionIds)");
        appendProductFilter(sb, params, filter);
        sb.append(SETTINGS_FINAL);

        Long result = jdbc.ch().queryForObject(sb.toString(), params, Long.class);
        return result != null ? result : 0L;
    }

    public List<PostingPnlResponse> findByPosting(List<Long> connectionIds, PnlFilter filter,
                                                   String sortColumn, int limit, long offset) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        var sb = new StringBuilder(BY_POSTING_SQL);
        appendPostingFilter(sb, params, filter);

        String orderBy = POSTING_SORT_WHITELIST.getOrDefault(sortColumn, "finance_date");
        sb.append(" ORDER BY ").append(orderBy).append(" DESC NULLS LAST");
        sb.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", limit);
        params.addValue("offset", offset);
        sb.append(SETTINGS_FINAL);

        return jdbc.ch().query(sb.toString(), params, this::mapPostingPnl);
    }

    public long countByPosting(List<Long> connectionIds, PnlFilter filter) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        var sb = new StringBuilder("SELECT count(*) FROM mart_posting_pnl WHERE connection_id IN (:connectionIds)");
        appendPostingFilter(sb, params, filter);
        sb.append(SETTINGS_FINAL);

        Long result = jdbc.ch().queryForObject(sb.toString(), params, Long.class);
        return result != null ? result : 0L;
    }

    public List<PostingDetailResponse> findPostingDetails(List<Long> connectionIds, String postingId) {
        var params = new MapSqlParameterSource()
                .addValue("connectionIds", connectionIds)
                .addValue("postingId", postingId);

        return jdbc.ch().query(POSTING_DETAIL_SQL, params, this::mapPostingDetail);
    }

    public List<PnlTrendResponse> findTrend(List<Long> connectionIds, PnlFilter filter,
                                             TrendGranularity granularity) {
        var params = new MapSqlParameterSource("connectionIds", connectionIds);
        String periodExpr = buildTrendPeriodExpression(granularity);

        var sql = """
                SELECT
                    %s AS period_label,
                    sum(revenue_amount) AS revenue_amount,
                    sum(marketplace_commission_amount) + sum(acquiring_commission_amount)
                        + sum(logistics_cost_amount) + sum(storage_cost_amount)
                        + sum(penalties_amount) + sum(marketing_cost_amount)
                        + sum(acceptance_cost_amount) + sum(other_marketplace_charges_amount) AS total_costs,
                    sum(refund_amount) AS refund_amount,
                    sum(compensation_amount) AS compensation_amount,
                    sum(advertising_cost) AS advertising_cost,
                    sum(net_cogs) AS net_cogs,
                    sum(marketplace_pnl) AS marketplace_pnl,
                    sum(full_pnl) AS full_pnl
                FROM mart_product_pnl
                WHERE connection_id IN (:connectionIds)
                """.formatted(periodExpr);

        var sb = new StringBuilder(sql);
        appendPeriodFilter(sb, params, filter);
        sb.append(" GROUP BY period_label ORDER BY period_label");
        sb.append(SETTINGS_FINAL);

        return jdbc.ch().query(sb.toString(), params, (rs, rowNum) -> new PnlTrendResponse(
                rs.getString("period_label"),
                rs.getBigDecimal("revenue_amount"),
                rs.getBigDecimal("total_costs"),
                rs.getBigDecimal("refund_amount"),
                rs.getBigDecimal("compensation_amount"),
                rs.getBigDecimal("advertising_cost"),
                rs.getBigDecimal("net_cogs"),
                rs.getBigDecimal("marketplace_pnl"),
                rs.getBigDecimal("full_pnl")
        ));
    }

    private void appendPeriodFilter(StringBuilder sb, MapSqlParameterSource params, PnlFilter filter) {
        if (filter.connectionId() != null) {
            sb.append(" AND connection_id = :connectionId");
            params.addValue("connectionId", filter.connectionId());
        }
        if (filter.from() != null) {
            sb.append(" AND period >= :periodFrom");
            params.addValue("periodFrom", toPeriod(filter.from()));
        }
        if (filter.to() != null) {
            sb.append(" AND period <= :periodTo");
            params.addValue("periodTo", toPeriod(filter.to()));
        }
    }

    private void appendProductFilter(StringBuilder sb, MapSqlParameterSource params, PnlFilter filter) {
        if (filter.connectionId() != null) {
            sb.append(" AND m.connection_id = :connectionId");
            params.addValue("connectionId", filter.connectionId());
        }
        if (filter.periodAsInt() != null) {
            sb.append(" AND m.period = :period");
            params.addValue("period", filter.periodAsInt());
        }
        if (filter.sellerSkuId() != null) {
            sb.append(" AND m.seller_sku_id = :sellerSkuId");
            params.addValue("sellerSkuId", filter.sellerSkuId());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            sb.append(" AND (p.product_name ILIKE :search OR p.sku_code ILIKE :search)");
            params.addValue("search", "%%" + filter.search().trim() + "%%");
        }
    }

    private void appendPostingFilter(StringBuilder sb, MapSqlParameterSource params, PnlFilter filter) {
        if (filter.connectionId() != null) {
            sb.append(" AND connection_id = :connectionId");
            params.addValue("connectionId", filter.connectionId());
        }
        if (filter.from() != null) {
            sb.append(" AND finance_date >= :dateFrom");
            params.addValue("dateFrom", filter.from());
        }
        if (filter.to() != null) {
            sb.append(" AND finance_date <= :dateTo");
            params.addValue("dateTo", filter.to());
        }
        if (filter.sellerSkuId() != null) {
            sb.append(" AND seller_sku_id = :sellerSkuId");
            params.addValue("sellerSkuId", filter.sellerSkuId());
        }
    }

    private String buildTrendPeriodExpression(TrendGranularity granularity) {
        return switch (granularity) {
            case DAILY -> "toString(toDate(toStartOfDay(toDateTime(toDate(concat(toString(intDiv(period, 100)), '-', toString(period % 100), '-01'))))))";
            case WEEKLY -> "toString(toStartOfWeek(toDate(concat(toString(intDiv(period, 100)), '-', toString(period % 100), '-01'))))";
            case MONTHLY -> "toString(period)";
        };
    }

    private int toPeriod(LocalDate date) {
        return date.getYear() * 100 + date.getMonthValue();
    }

    private PnlSummaryResponse mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new PnlSummaryResponse(
                rs.getLong("connection_id"),
                rs.getString("source_platform"),
                rs.getBigDecimal("revenue_amount"),
                rs.getBigDecimal("marketplace_commission_amount"),
                rs.getBigDecimal("acquiring_commission_amount"),
                rs.getBigDecimal("logistics_cost_amount"),
                rs.getBigDecimal("storage_cost_amount"),
                rs.getBigDecimal("penalties_amount"),
                rs.getBigDecimal("marketing_cost_amount"),
                rs.getBigDecimal("acceptance_cost_amount"),
                rs.getBigDecimal("other_marketplace_charges_amount"),
                rs.getBigDecimal("compensation_amount"),
                rs.getBigDecimal("refund_amount"),
                rs.getBigDecimal("net_payout"),
                rs.getBigDecimal("gross_cogs"),
                rs.getBigDecimal("net_cogs"),
                rs.getBigDecimal("advertising_cost"),
                rs.getBigDecimal("marketplace_pnl"),
                rs.getBigDecimal("full_pnl")
        );
    }

    private ProductPnlResponse mapProductPnl(ResultSet rs, int rowNum) throws SQLException {
        return new ProductPnlResponse(
                rs.getLong("connection_id"),
                rs.getString("source_platform"),
                rs.getLong("seller_sku_id"),
                rs.getLong("product_id"),
                rs.getInt("period"),
                rs.getString("attribution_level"),
                rs.getString("sku_code"),
                rs.getString("product_name"),
                rs.getBigDecimal("revenue_amount"),
                rs.getBigDecimal("marketplace_commission_amount"),
                rs.getBigDecimal("acquiring_commission_amount"),
                rs.getBigDecimal("logistics_cost_amount"),
                rs.getBigDecimal("storage_cost_amount"),
                rs.getBigDecimal("penalties_amount"),
                rs.getBigDecimal("marketing_cost_amount"),
                rs.getBigDecimal("acceptance_cost_amount"),
                rs.getBigDecimal("other_marketplace_charges_amount"),
                rs.getBigDecimal("compensation_amount"),
                rs.getBigDecimal("refund_amount"),
                rs.getBigDecimal("net_payout"),
                rs.getBigDecimal("gross_cogs"),
                rs.getBigDecimal("net_cogs"),
                rs.getString("cogs_status"),
                rs.getBigDecimal("advertising_cost"),
                rs.getBigDecimal("marketplace_pnl"),
                rs.getBigDecimal("full_pnl")
        );
    }

    private PostingPnlResponse mapPostingPnl(ResultSet rs, int rowNum) throws SQLException {
        return new PostingPnlResponse(
                rs.getString("posting_id"),
                rs.getLong("connection_id"),
                rs.getString("source_platform"),
                rs.getString("order_id"),
                getBoxedLong(rs, "seller_sku_id"),
                getBoxedLong(rs, "product_id"),
                rs.getDate("finance_date").toLocalDate(),
                rs.getBigDecimal("revenue_amount"),
                rs.getBigDecimal("marketplace_commission_amount"),
                rs.getBigDecimal("acquiring_commission_amount"),
                rs.getBigDecimal("logistics_cost_amount"),
                rs.getBigDecimal("storage_cost_amount"),
                rs.getBigDecimal("penalties_amount"),
                rs.getBigDecimal("marketing_cost_amount"),
                rs.getBigDecimal("acceptance_cost_amount"),
                rs.getBigDecimal("other_marketplace_charges_amount"),
                rs.getBigDecimal("compensation_amount"),
                rs.getBigDecimal("refund_amount"),
                rs.getBigDecimal("net_payout"),
                getBoxedInt(rs, "quantity"),
                rs.getBigDecimal("gross_cogs"),
                rs.getBigDecimal("net_cogs"),
                rs.getString("cogs_status"),
                rs.getBigDecimal("reconciliation_residual")
        );
    }

    private PostingDetailResponse mapPostingDetail(ResultSet rs, int rowNum) throws SQLException {
        return new PostingDetailResponse(
                rs.getLong("entry_id"),
                rs.getString("entry_type"),
                rs.getString("attribution_level"),
                rs.getDate("finance_date").toLocalDate(),
                rs.getBigDecimal("revenue_amount"),
                rs.getBigDecimal("marketplace_commission_amount"),
                rs.getBigDecimal("acquiring_commission_amount"),
                rs.getBigDecimal("logistics_cost_amount"),
                rs.getBigDecimal("storage_cost_amount"),
                rs.getBigDecimal("penalties_amount"),
                rs.getBigDecimal("marketing_cost_amount"),
                rs.getBigDecimal("acceptance_cost_amount"),
                rs.getBigDecimal("other_marketplace_charges_amount"),
                rs.getBigDecimal("compensation_amount"),
                rs.getBigDecimal("refund_amount"),
                rs.getBigDecimal("net_payout")
        );
    }

    private Long getBoxedLong(ResultSet rs, String column) throws SQLException {
        long val = rs.getLong(column);
        return rs.wasNull() ? null : val;
    }

    private Integer getBoxedInt(ResultSet rs, String column) throws SQLException {
        int val = rs.getInt(column);
        return rs.wasNull() ? null : val;
    }
}
