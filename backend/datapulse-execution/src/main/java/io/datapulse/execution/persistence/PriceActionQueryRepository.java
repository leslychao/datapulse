package io.datapulse.execution.persistence;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionStatus;
import io.datapulse.execution.domain.PriceActionFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PriceActionQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "id", "pa.id",
            "status", "pa.status",
            "targetPrice", "pa.target_price",
            "createdAt", "pa.created_at",
            "updatedAt", "pa.updated_at"
    );

    private static final String BASE_SELECT = """
            SELECT pa.id, pa.marketplace_offer_id,
                   mo.name AS offer_name, mo.marketplace_sku AS sku,
                   mc.marketplace_type AS marketplace, mc.name AS connection_name,
                   pa.execution_mode, pa.status,
                   pa.target_price, pa.current_price_at_creation,
                   pa.attempt_count, pa.max_attempts,
                   pa.created_at, pa.updated_at
            FROM price_action pa
            JOIN marketplace_offer mo ON pa.marketplace_offer_id = mo.id
            JOIN marketplace_connection mc ON mo.marketplace_connection_id = mc.id
            """;

    private static final String BASE_COUNT = """
            SELECT COUNT(*)
            FROM price_action pa
            JOIN marketplace_offer mo ON pa.marketplace_offer_id = mo.id
            JOIN marketplace_connection mc ON mo.marketplace_connection_id = mc.id
            """;

    public Page<PriceActionSummaryRow> findAll(long workspaceId,
                                                PriceActionFilter filter,
                                                Pageable pageable) {
        var whereClause = new StringBuilder(" WHERE pa.workspace_id = :workspaceId");
        var params = new MapSqlParameterSource("workspaceId", workspaceId);

        appendFilters(filter, whereClause, params);

        String countSql = BASE_COUNT + whereClause;
        Long total = jdbc.queryForObject(countSql, params, Long.class);
        if (total == null || total == 0) {
            return Page.empty(pageable);
        }

        String orderBy = buildOrderByClause(pageable.getSort());
        String querySql = BASE_SELECT + whereClause + orderBy
                + " LIMIT :limit OFFSET :offset";
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<PriceActionSummaryRow> content = jdbc.query(querySql, params, (rs, rowNum) ->
                new PriceActionSummaryRow(
                        rs.getLong("id"),
                        rs.getLong("marketplace_offer_id"),
                        rs.getString("offer_name"),
                        rs.getString("sku"),
                        rs.getString("marketplace"),
                        rs.getString("connection_name"),
                        ActionExecutionMode.valueOf(rs.getString("execution_mode")),
                        ActionStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("target_price"),
                        rs.getBigDecimal("current_price_at_creation"),
                        rs.getInt("attempt_count"),
                        rs.getInt("max_attempts"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ));

        return new PageImpl<>(content, pageable, total);
    }

    private void appendFilters(PriceActionFilter filter, StringBuilder where,
                                MapSqlParameterSource params) {
        if (filter == null) {
            return;
        }

        if (filter.connectionId() != null) {
            where.append("""
                     AND pa.marketplace_offer_id IN (
                       SELECT mo.id FROM marketplace_offer mo
                       WHERE mo.marketplace_connection_id = :connectionId
                     )
                    """);
            params.addValue("connectionId", filter.connectionId());
        }

        if (filter.marketplaceOfferId() != null) {
            where.append(" AND pa.marketplace_offer_id = :offerId");
            params.addValue("offerId", filter.marketplaceOfferId());
        }

        if (filter.status() != null) {
            where.append(" AND pa.status = :status");
            params.addValue("status", filter.status().name());
        }

        if (filter.executionMode() != null) {
            where.append(" AND pa.execution_mode = :executionMode");
            params.addValue("executionMode", filter.executionMode().name());
        }

        if (filter.from() != null) {
            where.append(" AND pa.created_at >= :from");
            params.addValue("from", filter.from().atStartOfDay());
        }

        if (filter.to() != null) {
            where.append(" AND pa.created_at < :to");
            params.addValue("to", filter.to().plusDays(1).atStartOfDay());
        }
    }

    private String buildOrderByClause(Sort sort) {
        if (sort.isUnsorted()) {
            return " ORDER BY pa.created_at DESC NULLS LAST";
        }

        var sb = new StringBuilder(" ORDER BY ");
        var orders = sort.stream().toList();
        for (int i = 0; i < orders.size(); i++) {
            Sort.Order order = orders.get(i);
            String column = SORT_COLUMNS.getOrDefault(order.getProperty(), "pa.created_at");
            sb.append(column).append(" ").append(order.getDirection().name())
                    .append(" NULLS LAST");
            if (i < orders.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}
