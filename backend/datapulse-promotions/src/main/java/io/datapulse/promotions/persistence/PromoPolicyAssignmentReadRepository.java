package io.datapulse.promotions.persistence;

import io.datapulse.promotions.api.PromoAssignmentResponse;
import io.datapulse.promotions.domain.PromoScopeType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PromoPolicyAssignmentReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String LIST_ENRICHED = """
      SELECT ppa.id,
             ppa.promo_policy_id,
             mc.name                       AS connection_name,
             mc.marketplace_type           AS marketplace,
             ppa.scope_type,
             ppa.category_id,
             ppa.marketplace_offer_id,
             ppa.created_at
        FROM promo_policy_assignment ppa
        JOIN marketplace_connection mc ON mc.id = ppa.marketplace_connection_id
       WHERE ppa.promo_policy_id = :policyId
       ORDER BY ppa.id
      """;

  public List<PromoAssignmentResponse> findEnrichedByPolicyId(long policyId) {
    return jdbc.query(LIST_ENRICHED, Map.of("policyId", policyId), this::mapRow);
  }

  private static final String FIND_ENRICHED_BY_ID = """
      SELECT ppa.id,
             ppa.promo_policy_id,
             mc.name                       AS connection_name,
             mc.marketplace_type           AS marketplace,
             ppa.scope_type,
             ppa.category_id,
             ppa.marketplace_offer_id,
             ppa.created_at
        FROM promo_policy_assignment ppa
        JOIN marketplace_connection mc ON mc.id = ppa.marketplace_connection_id
       WHERE ppa.id = :assignmentId
      """;

  public PromoAssignmentResponse findEnrichedById(long assignmentId) {
    var results = jdbc.query(FIND_ENRICHED_BY_ID,
        Map.of("assignmentId", assignmentId), this::mapRow);
    return results.isEmpty() ? null : results.get(0);
  }

  private PromoAssignmentResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new PromoAssignmentResponse(
        rs.getLong("id"),
        rs.getLong("promo_policy_id"),
        rs.getString("connection_name"),
        rs.getString("marketplace"),
        PromoScopeType.valueOf(rs.getString("scope_type")),
        rs.getObject("category_id", Long.class),
        rs.getObject("marketplace_offer_id", Long.class),
        rs.getObject("created_at", OffsetDateTime.class));
  }
}
