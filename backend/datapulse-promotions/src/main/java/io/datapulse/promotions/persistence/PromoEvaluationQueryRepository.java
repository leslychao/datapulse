package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.PromoEvaluationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PromoEvaluationQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String KPI_SELECT = """
        SELECT COUNT(*)::bigint AS total,
               COUNT(*) FILTER (WHERE pe.evaluation_result = 'PROFITABLE')::bigint AS profitable_count,
               COUNT(*) FILTER (WHERE pe.evaluation_result = 'MARGINAL')::bigint AS marginal_count,
               COUNT(*) FILTER (WHERE pe.evaluation_result IN ('UNPROFITABLE', 'INSUFFICIENT_STOCK', 'INSUFFICIENT_DATA'))::bigint AS unprofitable_count
        FROM promo_evaluation pe
        WHERE pe.workspace_id = :workspaceId
        """;

    public PromoEvaluationKpiRow findKpi(long workspaceId) {
        var params = new MapSqlParameterSource("workspaceId", workspaceId);
        return jdbcTemplate.queryForObject(
            KPI_SELECT,
            params,
            (rs, rowNum) -> new PromoEvaluationKpiRow(
                rs.getLong("total"),
                rs.getLong("profitable_count"),
                rs.getLong("marginal_count"),
                rs.getLong("unprofitable_count")));
    }

    public Page<PromoEvaluationEntity> findFiltered(long workspaceId, Long runId,
                                                      Long campaignId, Long marketplaceOfferId,
                                                      PromoEvaluationResult evaluationResult,
                                                      Pageable pageable) {
        var where = new StringBuilder("""
                SELECT pe.* FROM promo_evaluation pe
                """);
        var params = new MapSqlParameterSource().addValue("workspaceId", workspaceId);

        if (campaignId != null) {
            where.append("""
                    JOIN canonical_promo_product cpp
                        ON pe.canonical_promo_product_id = cpp.id
                    """);
        }

        where.append("WHERE pe.workspace_id = :workspaceId");

        if (runId != null) {
            where.append(" AND pe.promo_evaluation_run_id = :runId");
            params.addValue("runId", runId);
        }
        if (campaignId != null) {
            where.append(" AND cpp.canonical_promo_campaign_id = :campaignId");
            params.addValue("campaignId", campaignId);
        }
        if (marketplaceOfferId != null) {
            where.append(" AND pe.canonical_promo_product_id IN (")
                    .append("SELECT id FROM canonical_promo_product ")
                    .append("WHERE marketplace_offer_id = :marketplaceOfferId)");
            params.addValue("marketplaceOfferId", marketplaceOfferId);
        }
        if (evaluationResult != null) {
            where.append(" AND pe.evaluation_result = :evaluationResult");
            params.addValue("evaluationResult", evaluationResult.name());
        }

        var countSql = "SELECT count(*) FROM (" + where + ") sub";
        int total = Optional.ofNullable(
                jdbcTemplate.queryForObject(countSql, params, Integer.class)).orElse(0);

        where.append(" ORDER BY pe.created_at DESC LIMIT :limit OFFSET :offset");
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<PromoEvaluationEntity> content = jdbcTemplate.query(
                where.toString(), params, (rs, rowNum) -> {
                    var entity = new PromoEvaluationEntity();
                    entity.setId(rs.getLong("id"));
                    entity.setWorkspaceId(rs.getLong("workspace_id"));
                    entity.setPromoEvaluationRunId(rs.getLong("promo_evaluation_run_id"));
                    entity.setCanonicalPromoProductId(
                            rs.getLong("canonical_promo_product_id"));
                    entity.setPromoPolicyId(
                            rs.getObject("promo_policy_id", Long.class));
                    entity.setEvaluatedAt(
                            rs.getObject("evaluated_at", OffsetDateTime.class));
                    entity.setCurrentParticipationStatus(
                            rs.getString("current_participation_status"));
                    entity.setPromoPrice(rs.getBigDecimal("promo_price"));
                    entity.setRegularPrice(rs.getBigDecimal("regular_price"));
                    entity.setDiscountPct(rs.getBigDecimal("discount_pct"));
                    entity.setCogs(rs.getBigDecimal("cogs"));
                    entity.setMarginAtPromoPrice(
                            rs.getBigDecimal("margin_at_promo_price"));
                    entity.setMarginAtRegularPrice(
                            rs.getBigDecimal("margin_at_regular_price"));
                    entity.setMarginDeltaPct(rs.getBigDecimal("margin_delta_pct"));
                    entity.setEffectiveCostRate(
                            rs.getBigDecimal("effective_cost_rate"));
                    entity.setStockAvailable(
                            rs.getObject("stock_available", Integer.class));
                    entity.setExpectedPromoDurationDays(
                            rs.getObject("expected_promo_duration_days", Integer.class));
                    entity.setAvgDailyVelocity(rs.getBigDecimal("avg_daily_velocity"));
                    entity.setStockDaysOfCover(
                            rs.getBigDecimal("stock_days_of_cover"));
                    entity.setStockSufficient(
                            rs.getObject("stock_sufficient", Boolean.class));
                    String resultStr = rs.getString("evaluation_result");
                    if (resultStr != null) {
                        entity.setEvaluationResult(
                                PromoEvaluationResult.valueOf(resultStr));
                    }
                    entity.setSkipReason(rs.getString("skip_reason"));
                    entity.setCreatedAt(
                            rs.getObject("created_at", OffsetDateTime.class));
                    return entity;
                });

        return new PageImpl<>(content, pageable, total);
    }
}
