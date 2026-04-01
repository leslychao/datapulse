package io.datapulse.pricing.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ImpactPreviewReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  static final int MAX_PREVIEW_OFFERS = 5_000;

  private static final String PREVIEW_OFFERS_SQL = """
      SELECT DISTINCT ON (mo.id)
             mo.id             AS offer_id,
             mo.name           AS offer_name,
             ss.sku_code       AS seller_sku,
             mo.status         AS offer_status,
             COALESCE(cpc.discount_price, cpc.price) AS current_price,
             cp.cost_price     AS cogs,
             CASE WHEN mpl.id IS NOT NULL THEN true ELSE false END AS has_manual_lock
        FROM price_policy_assignment ppa
        JOIN marketplace_offer mo
          ON (ppa.scope_type = 'SKU' AND mo.id = ppa.marketplace_offer_id)
          OR (ppa.scope_type = 'CONNECTION' AND mo.marketplace_connection_id = ppa.marketplace_connection_id)
          OR (ppa.scope_type = 'CATEGORY'
              AND mo.marketplace_connection_id = ppa.marketplace_connection_id
              AND mo.category_id = ppa.category_id)
        JOIN marketplace_connection mc
          ON mc.id = mo.marketplace_connection_id
        JOIN seller_sku ss
          ON ss.id = mo.seller_sku_id
   LEFT JOIN canonical_price_current cpc
          ON cpc.marketplace_offer_id = mo.id
   LEFT JOIN cost_profile cp
          ON cp.seller_sku_id = mo.seller_sku_id
         AND cp.valid_to IS NULL
   LEFT JOIN manual_price_lock mpl
          ON mpl.marketplace_offer_id = mo.id
         AND mpl.unlocked_at IS NULL
         AND (mpl.expires_at IS NULL OR mpl.expires_at > now())
       WHERE ppa.price_policy_id = :policyId
         AND mc.workspace_id = :workspaceId
    ORDER BY mo.id
       LIMIT :maxRows
      """;

  public List<ImpactPreviewRow> findPreviewOffers(long policyId, long workspaceId) {
    var params = Map.of(
        "policyId", policyId,
        "workspaceId", workspaceId,
        "maxRows", MAX_PREVIEW_OFFERS);
    return jdbc.query(PREVIEW_OFFERS_SQL, params, this::mapRow);
  }

  private ImpactPreviewRow mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new ImpactPreviewRow(
        rs.getLong("offer_id"),
        rs.getString("offer_name"),
        rs.getString("seller_sku"),
        rs.getString("offer_status"),
        rs.getBigDecimal("current_price"),
        rs.getBigDecimal("cogs"),
        rs.getBoolean("has_manual_lock"));
  }
}
