package io.datapulse.pricing.persistence;

import io.datapulse.pricing.api.AssignmentResponse;
import io.datapulse.pricing.domain.ScopeType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PricePolicyAssignmentReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String LIST_ENRICHED = """
      SELECT ppa.id,
             ppa.price_policy_id,
             mc.name                       AS connection_name,
             mc.marketplace_type           AS marketplace,
             ppa.scope_type,
             ppa.category_id,
             cat.name                      AS category_name,
             ppa.marketplace_offer_id,
             mo.name                       AS offer_name,
             ss.sku_code                   AS seller_sku
        FROM price_policy_assignment ppa
        JOIN marketplace_connection mc ON mc.id = ppa.marketplace_connection_id
   LEFT JOIN category cat              ON cat.id = ppa.category_id
   LEFT JOIN marketplace_offer mo      ON mo.id = ppa.marketplace_offer_id
   LEFT JOIN seller_sku ss             ON ss.id = mo.seller_sku_id
       WHERE ppa.price_policy_id = :policyId
       ORDER BY ppa.id
      """;

  public List<AssignmentResponse> findEnrichedByPolicyId(long policyId) {
    return jdbc.query(LIST_ENRICHED, Map.of("policyId", policyId), this::mapRow);
  }

  private static final String FIND_ENRICHED_BY_ID = """
      SELECT ppa.id,
             ppa.price_policy_id,
             mc.name                       AS connection_name,
             mc.marketplace_type           AS marketplace,
             ppa.scope_type,
             ppa.category_id,
             cat.name                      AS category_name,
             ppa.marketplace_offer_id,
             mo.name                       AS offer_name,
             ss.sku_code                   AS seller_sku
        FROM price_policy_assignment ppa
        JOIN marketplace_connection mc ON mc.id = ppa.marketplace_connection_id
   LEFT JOIN category cat              ON cat.id = ppa.category_id
   LEFT JOIN marketplace_offer mo      ON mo.id = ppa.marketplace_offer_id
   LEFT JOIN seller_sku ss             ON ss.id = mo.seller_sku_id
       WHERE ppa.id = :assignmentId
      """;

  public AssignmentResponse findEnrichedById(long assignmentId) {
    var results = jdbc.query(FIND_ENRICHED_BY_ID,
        Map.of("assignmentId", assignmentId), this::mapRow);
    return results.isEmpty() ? null : results.get(0);
  }

  private AssignmentResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new AssignmentResponse(
        rs.getLong("id"),
        rs.getLong("price_policy_id"),
        rs.getString("connection_name"),
        rs.getString("marketplace"),
        ScopeType.valueOf(rs.getString("scope_type")),
        rs.getObject("category_id", Long.class),
        rs.getString("category_name"),
        rs.getObject("marketplace_offer_id", Long.class),
        rs.getString("offer_name"),
        rs.getString("seller_sku"));
  }
}
