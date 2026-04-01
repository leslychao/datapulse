package io.datapulse.sellerops.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class QueueItemSummaryJdbcRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String PRICE_ACTION_SUMMARY = """
      SELECT pa.id                       AS entity_id,
             mo.name                     AS offer_name,
             ss.sku_code,
             pa.status                   AS action_status,
             paa.error_message           AS last_error
      FROM price_action pa
      JOIN marketplace_offer mo ON mo.id = pa.marketplace_offer_id
      JOIN seller_sku ss ON ss.id = mo.seller_sku_id
      LEFT JOIN LATERAL (
          SELECT error_message
          FROM price_action_attempt
          WHERE price_action_id = pa.id
          ORDER BY attempt_number DESC
          LIMIT 1
      ) paa ON true
      WHERE pa.id IN (:entityIds)
      """;

  private static final String OFFER_SUMMARY = """
      SELECT mo.id                       AS entity_id,
             mo.name                     AS offer_name,
             ss.sku_code,
             mo.status                   AS offer_status,
             mc.marketplace_type
      FROM marketplace_offer mo
      JOIN seller_sku ss ON ss.id = mo.seller_sku_id
      JOIN marketplace_connection mc ON mc.id = mo.marketplace_connection_id
      WHERE mo.id IN (:entityIds)
      """;

  public Map<Long, Map<String, Object>> fetchSummaries(
      String entityType, List<Long> entityIds) {
    if (entityIds == null || entityIds.isEmpty()) {
      return Collections.emptyMap();
    }

    return switch (entityType) {
      case "price_action" -> fetchPriceActionSummaries(entityIds);
      case "marketplace_offer" -> fetchOfferSummaries(entityIds);
      default -> Collections.emptyMap();
    };
  }

  private Map<Long, Map<String, Object>> fetchPriceActionSummaries(List<Long> ids) {
    var params = new MapSqlParameterSource("entityIds", ids);
    return jdbc.query(PRICE_ACTION_SUMMARY, params, (rs, rowNum) -> {
      Map<String, Object> map = new HashMap<>();
      map.put("entityId", rs.getLong("entity_id"));
      map.put("offerName", rs.getString("offer_name"));
      map.put("skuCode", rs.getString("sku_code"));
      map.put("actionStatus", rs.getString("action_status"));
      map.put("lastError", rs.getString("last_error"));
      return map;
    }).stream().collect(Collectors.toMap(
        m -> (Long) m.remove("entityId"),
        m -> m
    ));
  }

  private Map<Long, Map<String, Object>> fetchOfferSummaries(List<Long> ids) {
    var params = new MapSqlParameterSource("entityIds", ids);
    return jdbc.query(OFFER_SUMMARY, params, (rs, rowNum) -> {
      Map<String, Object> map = new HashMap<>();
      map.put("entityId", rs.getLong("entity_id"));
      map.put("offerName", rs.getString("offer_name"));
      map.put("skuCode", rs.getString("sku_code"));
      map.put("offerStatus", rs.getString("offer_status"));
      map.put("marketplaceType", rs.getString("marketplace_type"));
      return map;
    }).stream().collect(Collectors.toMap(
        m -> (Long) m.remove("entityId"),
        m -> m
    ));
  }
}
