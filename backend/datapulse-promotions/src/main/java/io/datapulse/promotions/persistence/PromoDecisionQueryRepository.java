package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.PromoDecisionType;
import io.datapulse.promotions.domain.PromoExecutionMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PromoDecisionQueryRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  private static final String KPI_SELECT = """
      SELECT COUNT(*) FILTER (WHERE pd.decision_type = 'PARTICIPATE')::bigint AS participate_count,
             COUNT(*) FILTER (WHERE pd.decision_type = 'DECLINE')::bigint AS decline_count,
             COUNT(*) FILTER (WHERE pd.decision_type = 'PENDING_REVIEW')::bigint AS pending_review_count
      FROM promo_decision pd
      WHERE pd.workspace_id = :workspaceId
      """;

  public PromoDecisionKpiRow findKpi(long workspaceId) {
    var params = new MapSqlParameterSource("workspaceId", workspaceId);
    return jdbcTemplate.queryForObject(
        KPI_SELECT,
        params,
        (rs, rowNum) -> new PromoDecisionKpiRow(
            rs.getLong("participate_count"),
            rs.getLong("decline_count"),
            rs.getLong("pending_review_count")));
  }

  public Page<PromoDecisionEntity> findFiltered(long workspaceId,
      PromoDecisionType decisionType,
      Long campaignId,
      LocalDate from, LocalDate to,
      Pageable pageable) {
    var where = new StringBuilder("""
        SELECT pd.* FROM promo_decision pd
        WHERE pd.workspace_id = :workspaceId
        """);
    var params = new MapSqlParameterSource().addValue("workspaceId", workspaceId);

    if (decisionType != null) {
      where.append(" AND pd.decision_type = :decisionType");
      params.addValue("decisionType", decisionType.name());
    }

    if (campaignId != null) {
      where.append("""
           AND pd.canonical_promo_product_id IN (
             SELECT cpp.id FROM canonical_promo_product cpp
             WHERE cpp.canonical_promo_campaign_id = :campaignId)
          """);
      params.addValue("campaignId", campaignId);
    }

    if (from != null) {
      where.append(" AND pd.created_at >= :from");
      params.addValue("from", from.atStartOfDay());
    }

    if (to != null) {
      where.append(" AND pd.created_at < :to");
      params.addValue("to", to.plusDays(1).atStartOfDay());
    }

    var countSql = "SELECT count(*) FROM (" + where + ") sub";
    int total = Optional.ofNullable(jdbcTemplate.queryForObject(countSql, params, Integer.class))
        .orElse(0);

    where.append(" ORDER BY pd.created_at DESC LIMIT :limit OFFSET :offset");
    params.addValue("limit", pageable.getPageSize());
    params.addValue("offset", pageable.getOffset());

    List<PromoDecisionEntity> content = jdbcTemplate.query(
        where.toString(), params, (rs, rowNum) -> {
          var entity = new PromoDecisionEntity();
          entity.setId(rs.getLong("id"));
          entity.setWorkspaceId(rs.getLong("workspace_id"));
          entity.setCanonicalPromoProductId(rs.getLong("canonical_promo_product_id"));
          entity.setPromoEvaluationId(rs.getObject("promo_evaluation_id", Long.class));
          entity.setPolicyVersion(rs.getInt("policy_version"));
          entity.setPolicySnapshot(rs.getString("policy_snapshot"));
          entity.setDecisionType(PromoDecisionType.valueOf(rs.getString("decision_type")));
          entity.setParticipationMode(
              io.datapulse.promotions.domain.ParticipationMode.valueOf(
                  rs.getString("participation_mode")));
          entity.setExecutionMode(
              PromoExecutionMode.valueOf(rs.getString("execution_mode")));
          entity.setTargetPromoPrice(rs.getBigDecimal("target_promo_price"));
          entity.setExplanationSummary(rs.getString("explanation_summary"));
          entity.setDecidedBy(rs.getObject("decided_by", Long.class));
          entity.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
          return entity;
        });

    return new PageImpl<>(content, pageable, total);
  }
}
