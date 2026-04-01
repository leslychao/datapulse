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
public class PromoJournalJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String BASE_SELECT = """
            SELECT
                pd.id                           AS decision_id,
                pd.created_at                   AS decision_date,
                cpc.promo_name,
                cpc.promo_type,
                cpc.date_from                   AS period_from,
                cpc.date_to                     AS period_to,
                pe.evaluation_result,
                pd.decision_type                AS participation_decision,
                pact.status                     AS action_status,
                cpp.required_price,
                pe.margin_at_promo_price,
                pe.margin_delta_pct,
                pd.explanation_summary
            """;

    private static final String FROM_JOINS = """
            FROM promo_decision pd
            JOIN canonical_promo_product cpp ON cpp.id = pd.canonical_promo_product_id
            JOIN canonical_promo_campaign cpc ON cpc.id = cpp.canonical_promo_campaign_id
            LEFT JOIN promo_evaluation pe ON pe.id = pd.promo_evaluation_id
            LEFT JOIN promo_action pact ON pact.promo_decision_id = pd.id
            """;

    public Page<PromoJournalRow> findByOfferId(long workspaceId, long offerId,
                                                LocalDate from, LocalDate to,
                                                String decisionType, String actionStatus,
                                                Pageable pageable) {
        var where = new StringBuilder(
                " WHERE cpp.marketplace_offer_id = :offerId AND pd.workspace_id = :workspaceId");
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

        List<PromoJournalRow> content = jdbc.query(querySql, params, this::mapRow);
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
            where.append(" AND pact.status = :actionStatus");
            params.addValue("actionStatus", actionStatus.trim());
        }
    }

    private PromoJournalRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return PromoJournalRow.builder()
                .decisionId(rs.getLong("decision_id"))
                .decisionDate(rs.getObject("decision_date", OffsetDateTime.class))
                .promoName(rs.getString("promo_name"))
                .promoType(rs.getString("promo_type"))
                .periodFrom(rs.getObject("period_from", LocalDate.class))
                .periodTo(rs.getObject("period_to", LocalDate.class))
                .evaluationResult(rs.getString("evaluation_result"))
                .participationDecision(rs.getString("participation_decision"))
                .actionStatus(rs.getString("action_status"))
                .requiredPrice(rs.getBigDecimal("required_price"))
                .marginAtPromoPrice(rs.getBigDecimal("margin_at_promo_price"))
                .marginDeltaPct(rs.getBigDecimal("margin_delta_pct"))
                .explanationSummary(rs.getString("explanation_summary"))
                .build();
    }
}
