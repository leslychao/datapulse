package io.datapulse.sellerops.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ActionHistoryJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String BASE_SELECT = """
            SELECT
                pa.id                       AS action_id,
                pa.created_at,
                pa.status,
                pa.execution_mode,
                pa.target_price,
                pa.cancel_reason,
                pa.hold_reason,
                pa.manual_override_reason,
                paa.actual_price
            """;

    private static final String FROM_JOINS = """
            FROM price_action pa
            LEFT JOIN LATERAL (
                SELECT actual_price
                FROM price_action_attempt
                WHERE price_action_id = pa.id
                ORDER BY attempt_number DESC
                LIMIT 1
            ) paa ON true
            """;

    private static final String WHERE_CLAUSE =
            " WHERE pa.marketplace_offer_id = :offerId AND pa.workspace_id = :workspaceId";

    public Page<ActionHistoryRow> findByOfferId(long workspaceId, long offerId,
                                                 Pageable pageable) {
        var params = new MapSqlParameterSource()
                .addValue("offerId", offerId)
                .addValue("workspaceId", workspaceId);

        String countSql = "SELECT COUNT(*) " + FROM_JOINS + WHERE_CLAUSE;
        Long total = jdbc.queryForObject(countSql, params, Long.class);
        if (total == null || total == 0) {
            return Page.empty(pageable);
        }

        String querySql = BASE_SELECT + FROM_JOINS + WHERE_CLAUSE
                + " ORDER BY pa.created_at DESC LIMIT :limit OFFSET :offset";
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<ActionHistoryRow> content = jdbc.query(querySql, params, this::mapRow);
        return new PageImpl<>(content, pageable, total);
    }

    private ActionHistoryRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ActionHistoryRow.builder()
                .actionId(rs.getLong("action_id"))
                .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                .status(rs.getString("status"))
                .executionMode(rs.getString("execution_mode"))
                .targetPrice(rs.getBigDecimal("target_price"))
                .actualPrice(rs.getBigDecimal("actual_price"))
                .cancelReason(rs.getString("cancel_reason"))
                .holdReason(rs.getString("hold_reason"))
                .manualOverrideReason(rs.getString("manual_override_reason"))
                .build();
    }
}
