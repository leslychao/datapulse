package io.datapulse.pricing.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Read-only queries against canonical tables for pricing signal assembly.
 * Designed for batch reads (one query per signal type for all offers in a pricing run).
 */
@Repository
@RequiredArgsConstructor
public class PricingDataReadRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String OFFERS_BY_CONNECTION = """
            SELECT mo.id, mo.seller_sku_id, mo.category_id, mo.marketplace_connection_id, mo.status
            FROM marketplace_offer mo
            WHERE mo.marketplace_connection_id = :connectionId
            """;

    private static final String CURRENT_PRICES = """
            SELECT cpc.marketplace_offer_id, cpc.price, cpc.discount_price
            FROM canonical_price_current cpc
            WHERE cpc.marketplace_offer_id IN (:offerIds)
            """;

    private static final String TOTAL_STOCK = """
            SELECT csc.marketplace_offer_id, COALESCE(SUM(csc.available), 0) AS total_available
            FROM canonical_stock_current csc
            WHERE csc.marketplace_offer_id IN (:offerIds)
            GROUP BY csc.marketplace_offer_id
            """;

    private static final String CURRENT_COGS = """
            SELECT mo.id AS marketplace_offer_id, cp.cost_price
            FROM marketplace_offer mo
            JOIN cost_profile cp ON cp.seller_sku_id = mo.seller_sku_id
                AND cp.valid_to IS NULL
            WHERE mo.id IN (:offerIds)
            """;

    private static final String ACTIVE_LOCKS = """
            SELECT mpl.marketplace_offer_id
            FROM manual_price_lock mpl
            WHERE mpl.marketplace_offer_id IN (:offerIds)
              AND mpl.unlocked_at IS NULL
              AND (mpl.expires_at IS NULL OR mpl.expires_at > now())
            """;

    private static final String DATA_FRESHNESS = """
            SELECT mss.last_success_at
            FROM marketplace_sync_state mss
            WHERE mss.marketplace_connection_id = :connectionId
              AND mss.data_domain = 'FINANCE'
            """;

    private static final String LATEST_CHANGE_DECISIONS = """
            SELECT pd.marketplace_offer_id, MAX(pd.created_at) AS last_change_at
            FROM price_decision pd
            WHERE pd.marketplace_offer_id IN (:offerIds)
              AND pd.decision_type = 'CHANGE'
            GROUP BY pd.marketplace_offer_id
            """;

    private static final String PRICE_REVERSALS = """
            SELECT pd.marketplace_offer_id, COUNT(*) AS reversal_count
            FROM price_decision pd
            WHERE pd.marketplace_offer_id IN (:offerIds)
              AND pd.decision_type = 'CHANGE'
              AND pd.created_at >= :since
            GROUP BY pd.marketplace_offer_id
            """;

    public List<OfferRow> findOffersByConnection(long connectionId) {
        return jdbc.query(OFFERS_BY_CONNECTION,
                Map.of("connectionId", connectionId),
                (rs, rowNum) -> new OfferRow(
                        rs.getLong("id"),
                        rs.getLong("seller_sku_id"),
                        rs.getObject("category_id", Long.class),
                        rs.getLong("marketplace_connection_id"),
                        rs.getString("status")));
    }

    public Map<Long, BigDecimal> findCurrentPrices(List<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jdbc.query(CURRENT_PRICES,
                new MapSqlParameterSource("offerIds", offerIds),
                rs -> {
                    Map<Long, BigDecimal> result = new HashMap<>();
                    while (rs.next()) {
                        long offerId = rs.getLong("marketplace_offer_id");
                        BigDecimal discountPrice = rs.getBigDecimal("discount_price");
                        BigDecimal price = discountPrice != null ? discountPrice : rs.getBigDecimal("price");
                        result.put(offerId, price);
                    }
                    return result;
                });
    }

    public Map<Long, Integer> findTotalStock(List<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jdbc.query(TOTAL_STOCK,
                new MapSqlParameterSource("offerIds", offerIds),
                rs -> {
                    Map<Long, Integer> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("marketplace_offer_id"),
                                rs.getInt("total_available"));
                    }
                    return result;
                });
    }

    public Map<Long, BigDecimal> findCurrentCogs(List<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jdbc.query(CURRENT_COGS,
                new MapSqlParameterSource("offerIds", offerIds),
                rs -> {
                    Map<Long, BigDecimal> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("marketplace_offer_id"),
                                rs.getBigDecimal("cost_price"));
                    }
                    return result;
                });
    }

    public List<Long> findLockedOfferIds(List<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return Collections.emptyList();
        }
        return jdbc.queryForList(ACTIVE_LOCKS,
                new MapSqlParameterSource("offerIds", offerIds),
                Long.class);
    }

    public OffsetDateTime findDataFreshness(long connectionId) {
        List<OffsetDateTime> results = jdbc.queryForList(DATA_FRESHNESS,
                Map.of("connectionId", connectionId),
                OffsetDateTime.class);
        return results.isEmpty() ? null : results.get(0);
    }

    public Map<Long, OffsetDateTime> findLatestChangeDecisions(List<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jdbc.query(LATEST_CHANGE_DECISIONS,
                new MapSqlParameterSource("offerIds", offerIds),
                rs -> {
                    Map<Long, OffsetDateTime> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("marketplace_offer_id"),
                                rs.getObject("last_change_at", OffsetDateTime.class));
                    }
                    return result;
                });
    }

    public Map<Long, Integer> findPriceReversals(List<Long> offerIds, OffsetDateTime since) {
        if (offerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        var params = new MapSqlParameterSource("offerIds", offerIds)
                .addValue("since", since);
        return jdbc.query(PRICE_REVERSALS, params,
                rs -> {
                    Map<Long, Integer> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("marketplace_offer_id"),
                                rs.getInt("reversal_count"));
                    }
                    return result;
                });
    }

    public record OfferRow(
            long id,
            long sellerSkuId,
            Long categoryId,
            long connectionId,
            String status
    ) {
    }
}
