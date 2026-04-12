package io.datapulse.sellerops.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OfferDetailJdbcRepository {

  private final NamedParameterJdbcTemplate jdbc;

  private static final String DETAIL_QUERY = """
      SELECT
          mo.id                       AS offer_id,
          ss.id                       AS seller_sku_id,
          ss.sku_code,
          mo.name                     AS product_name,
          mc.marketplace_type,
          mc.id                       AS connection_id,
          mc.name                     AS connection_name,
          mo.status,
          cat.name                    AS category,
          cpc.price                   AS current_price,
          cpc.discount_price,
          cp.cost_price,
          CASE WHEN cpc.price > 0 AND cp.cost_price IS NOT NULL
               THEN ROUND((cpc.price - cp.cost_price) / NULLIF(cpc.price, 0) * 100, 2)
               ELSE NULL END          AS margin_pct,
          stock_agg.total_available   AS available_stock,
          sos.simulated_price,
          sos.price_delta_pct         AS simulated_delta_pct,
          mss.last_success_at         AS last_sync_at,

          pp.policy_id,
          pp.policy_name,
          pp.strategy_type,
          pp.policy_execution_mode,

          latest_pd.decision_id,
          latest_pd.decision_type,
          latest_pd.decision_current_price,
          latest_pd.decision_target_price,
          latest_pd.decision_explanation,
          latest_pd.decision_created_at,

          latest_pa.action_id,
          latest_pa.action_status,
          latest_pa.action_target_price,
          latest_pa.action_execution_mode,
          latest_pa.action_created_at,

          active_promo.participation_status     AS promo_participation_status,
          active_promo.campaign_name            AS promo_campaign_name,
          active_promo.promo_price,
          active_promo.promo_ends_at,

          mpl.lock_id,
          mpl.locked_price,
          mpl.lock_reason,
          mpl.locked_at

      FROM marketplace_offer mo
      JOIN seller_sku ss ON ss.id = mo.seller_sku_id
      JOIN marketplace_connection mc ON mc.id = mo.marketplace_connection_id
      LEFT JOIN category cat ON cat.id = mo.category_id
      LEFT JOIN canonical_price_current cpc ON cpc.marketplace_offer_id = mo.id
      LEFT JOIN LATERAL (
          SELECT cost_price
          FROM cost_profile
          WHERE seller_sku_id = ss.id
            AND valid_from <= CURRENT_DATE
            AND (valid_to IS NULL OR valid_to > CURRENT_DATE)
          ORDER BY valid_from DESC
          LIMIT 1
      ) cp ON true
      LEFT JOIN (
          SELECT marketplace_offer_id, SUM(available) AS total_available
          FROM canonical_stock_current
          GROUP BY marketplace_offer_id
      ) stock_agg ON stock_agg.marketplace_offer_id = mo.id
      LEFT JOIN LATERAL (
          SELECT pp2.id    AS policy_id,
                 pp2.name  AS policy_name,
                 pp2.strategy_type,
                 pp2.execution_mode AS policy_execution_mode
          FROM price_policy_assignment ppa
          JOIN price_policy pp2 ON pp2.id = ppa.price_policy_id AND pp2.status = 'ACTIVE'
          WHERE ppa.marketplace_connection_id = mc.id
            AND (ppa.marketplace_offer_id = mo.id
                 OR (ppa.scope_type = 'CATEGORY' AND ppa.category_id = mo.category_id)
                 OR ppa.scope_type = 'CONNECTION')
          ORDER BY
              CASE ppa.scope_type
                  WHEN 'OFFER' THEN 1
                  WHEN 'CATEGORY' THEN 2
                  WHEN 'CONNECTION' THEN 3
              END,
              pp2.priority DESC
          LIMIT 1
      ) pp ON true
      LEFT JOIN LATERAL (
          SELECT pd.id            AS decision_id,
                 pd.decision_type,
                 pd.current_price AS decision_current_price,
                 pd.target_price  AS decision_target_price,
                 pd.explanation_summary AS decision_explanation,
                 pd.created_at    AS decision_created_at
          FROM price_decision pd
          WHERE pd.marketplace_offer_id = mo.id
          ORDER BY pd.created_at DESC
          LIMIT 1
      ) latest_pd ON true
      LEFT JOIN LATERAL (
          SELECT pa.id             AS action_id,
                 pa.status         AS action_status,
                 pa.target_price   AS action_target_price,
                 pa.execution_mode AS action_execution_mode,
                 pa.created_at     AS action_created_at
          FROM price_action pa
          WHERE pa.marketplace_offer_id = mo.id
          ORDER BY pa.created_at DESC
          LIMIT 1
      ) latest_pa ON true
      LEFT JOIN LATERAL (
          SELECT cpp.participation_status,
                 cpcam.promo_name    AS campaign_name,
                 cpp.required_price  AS promo_price,
                 cpcam.date_to       AS promo_ends_at
          FROM canonical_promo_product cpp
          JOIN canonical_promo_campaign cpcam
              ON cpcam.id = cpp.canonical_promo_campaign_id
          WHERE cpp.marketplace_offer_id = mo.id
            AND cpp.participation_status = 'PARTICIPATING'
            AND (cpcam.date_to IS NULL OR cpcam.date_to > NOW())
          LIMIT 1
      ) active_promo ON true
      LEFT JOIN LATERAL (
          SELECT lock2.id         AS lock_id,
                 lock2.locked_price,
                 lock2.reason     AS lock_reason,
                 lock2.locked_at  AS locked_at
          FROM manual_price_lock lock2
          WHERE lock2.marketplace_offer_id = mo.id
            AND lock2.unlocked_at IS NULL
          LIMIT 1
      ) mpl ON true
      LEFT JOIN simulated_offer_state sos ON sos.marketplace_offer_id = mo.id AND sos.workspace_id = mc.workspace_id
      LEFT JOIN LATERAL (
          SELECT MIN(last_success_at) AS last_success_at
          FROM marketplace_sync_state
          WHERE marketplace_connection_id = mc.id
      ) mss ON true
      WHERE mc.workspace_id = :workspaceId AND mo.id = :offerId
      """;

  public Optional<OfferDetailRow> findById(long workspaceId, long offerId) {
    var params = new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("offerId", offerId);

    List<OfferDetailRow> rows = jdbc.query(DETAIL_QUERY, params, this::mapRow);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  private OfferDetailRow mapRow(ResultSet rs, int rowNum) throws SQLException {
    return OfferDetailRow.builder()
        .offerId(rs.getLong("offer_id"))
        .sellerSkuId(rs.getLong("seller_sku_id"))
        .skuCode(rs.getString("sku_code"))
        .productName(rs.getString("product_name"))
        .marketplaceType(rs.getString("marketplace_type"))
        .connectionId(rs.getLong("connection_id"))
        .connectionName(rs.getString("connection_name"))
        .status(rs.getString("status"))
        .category(rs.getString("category"))
        .currentPrice(rs.getBigDecimal("current_price"))
        .discountPrice(rs.getBigDecimal("discount_price"))
        .costPrice(rs.getBigDecimal("cost_price"))
        .marginPct(rs.getBigDecimal("margin_pct"))
        .availableStock(getBoxedInt(rs, "available_stock"))
        .simulatedPrice(rs.getBigDecimal("simulated_price"))
        .simulatedDeltaPct(rs.getBigDecimal("simulated_delta_pct"))
        .lastSyncAt(rs.getObject("last_sync_at", OffsetDateTime.class))
        .policyId(getBoxedLong(rs, "policy_id"))
        .policyName(rs.getString("policy_name"))
        .strategyType(rs.getString("strategy_type"))
        .policyExecutionMode(rs.getString("policy_execution_mode"))
        .decisionId(getBoxedLong(rs, "decision_id"))
        .decisionType(rs.getString("decision_type"))
        .decisionCurrentPrice(rs.getBigDecimal("decision_current_price"))
        .decisionTargetPrice(rs.getBigDecimal("decision_target_price"))
        .decisionExplanation(rs.getString("decision_explanation"))
        .decisionCreatedAt(rs.getObject("decision_created_at", OffsetDateTime.class))
        .actionId(getBoxedLong(rs, "action_id"))
        .actionStatus(rs.getString("action_status"))
        .actionTargetPrice(rs.getBigDecimal("action_target_price"))
        .actionExecutionMode(rs.getString("action_execution_mode"))
        .actionCreatedAt(rs.getObject("action_created_at", OffsetDateTime.class))
        .promoParticipationStatus(rs.getString("promo_participation_status"))
        .promoCampaignName(rs.getString("promo_campaign_name"))
        .promoPrice(rs.getBigDecimal("promo_price"))
        .promoEndsAt(rs.getObject("promo_ends_at", OffsetDateTime.class))
        .lockId(getBoxedLong(rs, "lock_id"))
        .lockedPrice(rs.getBigDecimal("locked_price"))
        .lockReason(rs.getString("lock_reason"))
        .lockedAt(rs.getObject("locked_at", OffsetDateTime.class))
        .build();
  }

  private Integer getBoxedInt(ResultSet rs, String column) throws SQLException {
    int val = rs.getInt(column);
    return rs.wasNull() ? null : val;
  }

  private Long getBoxedLong(ResultSet rs, String column) throws SQLException {
    long val = rs.getLong(column);
    return rs.wasNull() ? null : val;
  }
}
