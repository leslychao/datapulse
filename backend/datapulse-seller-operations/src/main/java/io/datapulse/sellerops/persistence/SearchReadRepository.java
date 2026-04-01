package io.datapulse.sellerops.persistence;

import io.datapulse.sellerops.api.SearchResultResponse.PolicyResult;
import io.datapulse.sellerops.api.SearchResultResponse.ProductResult;
import io.datapulse.sellerops.api.SearchResultResponse.PromoResult;
import io.datapulse.sellerops.api.SearchResultResponse.ViewResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SearchReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public List<ProductResult> searchProducts(long workspaceId, String pattern, int limit) {
    return jdbc.query(PRODUCTS_SQL,
        searchParams(workspaceId, pattern, limit),
        (rs, rowNum) -> new ProductResult(
            rs.getLong("offer_id"),
            rs.getString("sku_code"),
            rs.getString("product_name"),
            rs.getString("marketplace_type")));
  }

  public List<PolicyResult> searchPolicies(long workspaceId, String pattern, int limit) {
    return jdbc.query(POLICIES_SQL,
        searchParams(workspaceId, pattern, limit),
        (rs, rowNum) -> new PolicyResult(
            rs.getLong("policy_id"),
            rs.getString("name")));
  }

  public List<PromoResult> searchPromos(long workspaceId, String pattern, int limit) {
    return jdbc.query(PROMOS_SQL,
        searchParams(workspaceId, pattern, limit),
        (rs, rowNum) -> new PromoResult(
            rs.getLong("campaign_id"),
            rs.getString("name")));
  }

  public List<ViewResult> searchViews(long workspaceId, String pattern, int limit) {
    return jdbc.query(VIEWS_SQL,
        searchParams(workspaceId, pattern, limit),
        (rs, rowNum) -> new ViewResult(
            rs.getLong("view_id"),
            rs.getString("name")));
  }

  private MapSqlParameterSource searchParams(
      long workspaceId, String pattern, int limit) {
    return new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("pattern", pattern)
        .addValue("limit", limit);
  }

  private static final String PRODUCTS_SQL = """
      SELECT co.id AS offer_id,
             co.marketplace_sku AS sku_code,
             co.product_name,
             co.marketplace_type
      FROM canonical_offer co
      WHERE co.workspace_id = :workspaceId
        AND (co.marketplace_sku ILIKE :pattern
             OR co.product_name ILIKE :pattern
             OR co.barcode ILIKE :pattern)
      ORDER BY co.product_name
      LIMIT :limit
      """;

  private static final String POLICIES_SQL = """
      SELECT id AS policy_id, name
      FROM price_policy
      WHERE workspace_id = :workspaceId
        AND name ILIKE :pattern
      ORDER BY name
      LIMIT :limit
      """;

  private static final String PROMOS_SQL = """
      SELECT id AS campaign_id, name
      FROM promo_policy
      WHERE workspace_id = :workspaceId
        AND name ILIKE :pattern
      ORDER BY name
      LIMIT :limit
      """;

  private static final String VIEWS_SQL = """
      SELECT id AS view_id, name
      FROM saved_view
      WHERE workspace_id = :workspaceId
        AND name ILIKE :pattern
      ORDER BY name
      LIMIT :limit
      """;
}
