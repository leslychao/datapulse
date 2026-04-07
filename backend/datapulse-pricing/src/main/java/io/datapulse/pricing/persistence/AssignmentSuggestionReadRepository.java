package io.datapulse.pricing.persistence;

import io.datapulse.pricing.api.CategorySuggestionResponse;
import io.datapulse.pricing.api.OfferSuggestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class AssignmentSuggestionReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String CATEGORIES_SQL = """
      SELECT cat.id,
             cat.name,
             cat.external_category_id
        FROM category cat
       WHERE cat.marketplace_connection_id = :connectionId
       ORDER BY cat.name
       LIMIT 200
      """;

  private static final String CATEGORIES_SEARCH_SQL = """
      SELECT cat.id,
             cat.name,
             cat.external_category_id
        FROM category cat
       WHERE cat.marketplace_connection_id = :connectionId
         AND (cat.name ILIKE :pattern OR cat.external_category_id ILIKE :pattern)
       ORDER BY cat.name
       LIMIT 50
      """;

  public List<CategorySuggestionResponse> findCategories(long connectionId, String search) {
    if (search == null || search.isBlank()) {
      return jdbc.query(CATEGORIES_SQL,
          Map.of("connectionId", connectionId),
          (rs, rowNum) -> new CategorySuggestionResponse(
              rs.getLong("id"),
              rs.getString("name"),
              rs.getString("external_category_id")));
    }

    Map<String, Object> params = new HashMap<>();
    params.put("connectionId", connectionId);
    params.put("pattern", "%" + search.trim() + "%");
    return jdbc.query(CATEGORIES_SEARCH_SQL, params,
        (rs, rowNum) -> new CategorySuggestionResponse(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("external_category_id")));
  }

  private static final String OFFERS_SEARCH_SQL = """
      SELECT mo.id,
             mo.name,
             mo.marketplace_sku,
             ss.sku_code AS seller_sku
        FROM marketplace_offer mo
        JOIN seller_sku ss ON ss.id = mo.seller_sku_id
       WHERE mo.marketplace_connection_id = :connectionId
         AND mo.status = 'ACTIVE'
         AND (mo.name ILIKE :pattern
              OR mo.marketplace_sku ILIKE :pattern
              OR ss.sku_code ILIKE :pattern)
       ORDER BY mo.name
       LIMIT 30
      """;

  public List<OfferSuggestionResponse> searchOffers(long connectionId, String search) {
    if (search == null || search.isBlank()) {
      return List.of();
    }

    Map<String, Object> params = new HashMap<>();
    params.put("connectionId", connectionId);
    params.put("pattern", "%" + search.trim() + "%");
    return jdbc.query(OFFERS_SEARCH_SQL, params,
        (rs, rowNum) -> new OfferSuggestionResponse(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("marketplace_sku"),
            rs.getString("seller_sku")));
  }
}
