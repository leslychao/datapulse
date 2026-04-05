package io.datapulse.analytics.persistence;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecommendationOfferReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String OFFER_DATA_SQL = """
      SELECT
          mo.id AS offer_id,
          mo.marketplace_connection_id AS connection_id,
          mo.marketplace_sku,
          cpc.price AS current_price,
          cp.cost_price,
          CASE WHEN cpc.price > 0 AND cp.cost_price IS NOT NULL
               THEN ROUND((cpc.price - cp.cost_price)
                    / NULLIF(cpc.price, 0) * 100, 2)
               ELSE NULL END AS margin_pct,
          COALESCE(cat.name, '') AS category
      FROM marketplace_offer mo
      JOIN marketplace_connection mc ON mc.id = mo.marketplace_connection_id
      LEFT JOIN canonical_price_current cpc ON cpc.marketplace_offer_id = mo.id
      LEFT JOIN LATERAL (
          SELECT cost_price
          FROM cost_profile
          WHERE marketplace_offer_id = mo.id
          ORDER BY effective_from DESC
          LIMIT 1
      ) cp ON true
      LEFT JOIN category cat ON cat.id = mo.category_id
      WHERE mo.id IN (:offerIds)
        AND mc.workspace_id = :workspaceId
      """;

  public List<RecommendationOfferRow> findOfferData(
      long workspaceId, List<Long> offerIds) {

    if (offerIds == null || offerIds.isEmpty()) {
      return Collections.emptyList();
    }

    var params = new MapSqlParameterSource()
        .addValue("offerIds", offerIds)
        .addValue("workspaceId", workspaceId);

    return jdbc.query(OFFER_DATA_SQL, params, (rs, rowNum) -> {
      BigDecimal marginRaw = rs.getBigDecimal("margin_pct");
      return new RecommendationOfferRow(
          rs.getLong("offer_id"),
          rs.getLong("connection_id"),
          rs.getString("marketplace_sku"),
          rs.getBigDecimal("current_price"),
          rs.getBigDecimal("cost_price"),
          marginRaw,
          rs.getString("category")
      );
    });
  }
}
