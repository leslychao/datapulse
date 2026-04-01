package io.datapulse.sellerops.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PriceJournalJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String BASE_SELECT = """
            SELECT
                pd.id                       AS decision_id,
                pd.created_at               AS decision_date,
                pd.decision_type,
                pd.skip_reason,
                pd.policy_snapshot->>'name'  AS policy_name,
                pd.policy_version,
                pd.current_price,
                pd.target_price,
                pd.price_change_pct,
                pa.status                   AS action_status,
                pa.execution_mode,
                paa.actual_price,
                paa.reconciliation_source,
                pd.explanation_summary
            """;

    private static final String FROM_JOINS = """
            FROM price_decision pd
            LEFT JOIN price_action pa ON pa.price_decision_id = pd.id
            LEFT JOIN LATERAL (
                SELECT actual_price, reconciliation_source
                FROM price_action_attempt
                WHERE price_action_id = pa.id
                ORDER BY attempt_number DESC
                LIMIT 1
            ) paa ON true
            """;

    public Page<PriceJournalRow> findByOfferId(long workspaceId, long offerId,
                                                LocalDate from, LocalDate to,
                                                String decisionType, String actionStatus,
                                                Pageable pageable) {
        var where = new StringBuilder(" WHERE pd.marketplace_offer_id = :offerId AND pd.workspace_id = :workspaceId");
        var params = new MapSqlParameterSource()
                .addValue("offerId", offerId)
                .addValue("workspaceId", workspaceId);

        appendFilters(where, params, from, to, decisionType, actionStatus);

        String countSql = "SELECT COUNT(*) " + FROM_JOINS + where;
        Long total = jdbc.queryForObject(countSql, params, Long.class);
        if (total == null || total == 0) {
            return Page.empty(pageable);
        }

        String querySql = BASE_SELECT + FROM_JOINS + where
                + " ORDER BY pd.created_at DESC LIMIT :limit OFFSET :offset";
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<PriceJournalRow> content = jdbc.query(querySql, params, this::mapRow);
        return new PageImpl<>(content, pageable, total);
    }

    private void appendFilters(StringBuilder where, MapSqlParameterSource params,
                               LocalDate from, LocalDate to,
                               String decisionType, String actionStatus) {
        if (from != null) {
            where.append(" AND pd.created_at >= :dateFrom");
            params.addValue("dateFrom", from.atStartOfDay());
        }
        if (to != null) {
            where.append(" AND pd.created_at < :dateTo");
            params.addValue("dateTo", to.plusDays(1).atStartOfDay());
        }
        if (StringUtils.hasText(decisionType)) {
            where.append(" AND pd.decision_type = :decisionType");
            params.addValue("decisionType", decisionType.trim());
        }
        if (StringUtils.hasText(actionStatus)) {
            where.append(" AND pa.status = :actionStatus");
            params.addValue("actionStatus", actionStatus.trim());
        }
    }

    private PriceJournalRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return PriceJournalRow.builder()
                .decisionId(rs.getLong("decision_id"))
                .decisionDate(rs.getObject("decision_date", OffsetDateTime.class))
                .decisionType(rs.getString("decision_type"))
                .skipReason(rs.getString("skip_reason"))
                .policyName(rs.getString("policy_name"))
                .policyVersion(rs.getInt("policy_version"))
                .currentPrice(rs.getBigDecimal("current_price"))
                .targetPrice(rs.getBigDecimal("target_price"))
                .priceChangePct(rs.getBigDecimal("price_change_pct"))
                .actionStatus(rs.getString("action_status"))
                .executionMode(rs.getString("execution_mode"))
                .actualPrice(rs.getBigDecimal("actual_price"))
                .reconciliationSource(rs.getString("reconciliation_source"))
                .explanationSummary(rs.getString("explanation_summary"))
                .build();
    }
}
