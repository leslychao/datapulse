package io.datapulse.pricing.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            SELECT cpc.marketplace_offer_id, cpc.price, cpc.discount_price, cpc.min_price
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

    private static final String PROMO_ACTIVE_OFFERS = """
            SELECT DISTINCT pp.marketplace_offer_id
            FROM canonical_promo_product pp
            JOIN canonical_promo_campaign pc ON pp.canonical_promo_campaign_id = pc.id
            WHERE pp.marketplace_offer_id IN (:offerIds)
              AND pp.participation_status = 'PARTICIPATING'
              AND pc.status IN ('ACTIVE', 'UPCOMING', 'FROZEN')
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

    public Map<Long, CurrentPriceRow> findCurrentPrices(List<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jdbc.query(CURRENT_PRICES,
                new MapSqlParameterSource("offerIds", offerIds),
                rs -> {
                    Map<Long, CurrentPriceRow> result = new HashMap<>();
                    while (rs.next()) {
                        long offerId = rs.getLong("marketplace_offer_id");
                        BigDecimal discountPrice = rs.getBigDecimal("discount_price");
                        BigDecimal effectivePrice =
                                discountPrice != null ? discountPrice : rs.getBigDecimal("price");
                        BigDecimal minPrice = rs.getBigDecimal("min_price");
                        result.put(offerId, new CurrentPriceRow(effectivePrice, minPrice));
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

    public Set<Long> findPromoActiveOfferIds(List<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> ids = jdbc.queryForList(PROMO_ACTIVE_OFFERS,
                new MapSqlParameterSource("offerIds", offerIds),
                Long.class);
        return Set.copyOf(ids);
    }

    private static final String OFFERS_BY_IDS_ENRICHED = """
            SELECT mo.id,
                   mo.seller_sku_id,
                   mo.category_id,
                   mo.marketplace_connection_id,
                   mo.status,
                   mo.sku_code,
                   mo.product_name,
                   cpc.price AS current_price,
                   cpc.discount_price,
                   cp.cost_price AS cogs
            FROM marketplace_offer mo
            LEFT JOIN canonical_price_current cpc ON cpc.marketplace_offer_id = mo.id
            LEFT JOIN cost_profile cp ON cp.seller_sku_id = mo.seller_sku_id AND cp.valid_to IS NULL
            WHERE mo.id IN (:offerIds)
              AND mo.workspace_id = :workspaceId
            """;

    public List<EnrichedOfferRow> findOffersByIds(List<Long> offerIds, long workspaceId) {
        if (offerIds.isEmpty()) {
            return Collections.emptyList();
        }
        var params = new MapSqlParameterSource("offerIds", offerIds)
                .addValue("workspaceId", workspaceId);
        return jdbc.query(OFFERS_BY_IDS_ENRICHED, params,
                (rs, rowNum) -> new EnrichedOfferRow(
                        rs.getLong("id"),
                        rs.getLong("seller_sku_id"),
                        rs.getObject("category_id", Long.class),
                        rs.getLong("marketplace_connection_id"),
                        rs.getString("status"),
                        rs.getString("sku_code"),
                        rs.getString("product_name"),
                        resolveEffectivePrice(rs),
                        rs.getBigDecimal("cogs")));
    }

    private BigDecimal resolveEffectivePrice(ResultSet rs) throws SQLException {
        BigDecimal discountPrice = rs.getBigDecimal("discount_price");
        return discountPrice != null ? discountPrice : rs.getBigDecimal("current_price");
    }

    public record OfferRow(
            long id,
            long sellerSkuId,
            Long categoryId,
            long connectionId,
            String status
    ) {
    }

    public record CurrentPriceRow(
            BigDecimal effectivePrice,
            BigDecimal minPrice
    ) {
    }

    private static final String FAILED_ACTIONS_COUNT = """
            SELECT count(*) FROM price_action pa
            WHERE pa.workspace_id = :workspaceId
              AND pa.status = 'FAILED'
              AND pa.updated_at >= :since
            """;

    public boolean hasFailedActionsInPeriod(long workspaceId, OffsetDateTime since) {
        Integer count = jdbc.queryForObject(FAILED_ACTIONS_COUNT,
                new MapSqlParameterSource()
                        .addValue("workspaceId", workspaceId)
                        .addValue("since", since),
                Integer.class);
        return count != null && count > 0;
    }

    public record EnrichedOfferRow(
            long id,
            long sellerSkuId,
            Long categoryId,
            long connectionId,
            String status,
            String skuCode,
            String productName,
            BigDecimal currentPrice,
            BigDecimal cogs
    ) {
    }

    private static final String MARKETPLACE_SKUS = """
            SELECT mo.id, mo.marketplace_sku
            FROM marketplace_offer mo
            WHERE mo.id IN (:offerIds)
            """;

    public Map<Long, String> findMarketplaceSkus(List<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jdbc.query(MARKETPLACE_SKUS,
                new MapSqlParameterSource("offerIds", offerIds),
                rs -> {
                    Map<Long, String> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("id"),
                                rs.getString("marketplace_sku"));
                    }
                    return result;
                });
    }

    private static final String OFFER_STATUSES = """
            SELECT mo.id, mo.status
            FROM marketplace_offer mo
            WHERE mo.id IN (:offerIds)
            """;

    public Map<Long, String> findOfferStatuses(List<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jdbc.query(OFFER_STATUSES,
                new MapSqlParameterSource("offerIds", offerIds),
                rs -> {
                    Map<Long, String> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("id"), rs.getString("status"));
                    }
                    return result;
                });
    }

    private static final String COMPETITOR_SIGNALS = """
            SELECT
                cm.marketplace_offer_id,
                cm.trust_level,
                co.competitor_price,
                co.observed_at
            FROM competitor_match cm
            JOIN LATERAL (
                SELECT co.competitor_price, co.observed_at
                FROM competitor_observation co
                WHERE co.competitor_match_id = cm.id
                ORDER BY co.observed_at DESC
                LIMIT 1
            ) co ON true
            WHERE cm.marketplace_offer_id IN (:offerIds)
              AND cm.trust_level IN ('TRUSTED', 'CANDIDATE')
            """;

    public List<CompetitorSignalRow> findCompetitorSignals(List<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return Collections.emptyList();
        }
        return jdbc.query(COMPETITOR_SIGNALS,
                new MapSqlParameterSource("offerIds", offerIds),
                (rs, rowNum) -> new CompetitorSignalRow(
                        rs.getLong("marketplace_offer_id"),
                        rs.getString("trust_level"),
                        rs.getBigDecimal("competitor_price"),
                        rs.getObject("observed_at", OffsetDateTime.class)));
    }

    public record CompetitorSignalRow(
            long marketplaceOfferId,
            String trustLevel,
            BigDecimal competitorPrice,
            OffsetDateTime observedAt
    ) {
    }

    private static final String OFFERS_BY_SKU_CODES = """
            SELECT mo.id, mo.seller_sku_id, mo.category_id,
                   mo.marketplace_connection_id, mo.status
            FROM marketplace_offer mo
            WHERE mo.workspace_id = :workspaceId
              AND CAST(mo.seller_sku_id AS varchar) IN (:skuCodes)
            """;

    public List<OfferRow> findOffersBySkuCodes(long workspaceId, List<String> skuCodes) {
        if (skuCodes.isEmpty()) {
            return Collections.emptyList();
        }
        var params = new MapSqlParameterSource("skuCodes", skuCodes)
                .addValue("workspaceId", workspaceId);
        return jdbc.query(OFFERS_BY_SKU_CODES, params,
                (rs, rowNum) -> new OfferRow(
                        rs.getLong("id"),
                        rs.getLong("seller_sku_id"),
                        rs.getObject("category_id", Long.class),
                        rs.getLong("marketplace_connection_id"),
                        rs.getString("status")));
    }

    private static final String SELLER_SKU_IDS = """
            SELECT mo.id, mo.seller_sku_id
            FROM marketplace_offer mo
            WHERE mo.id IN (:offerIds)
              AND mo.seller_sku_id IS NOT NULL
            """;

    public Map<Long, Long> findSellerSkuIds(List<Long> offerIds) {
        if (offerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jdbc.query(SELLER_SKU_IDS,
                new MapSqlParameterSource("offerIds", offerIds),
                rs -> {
                    Map<Long, Long> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("id"),
                                rs.getLong("seller_sku_id"));
                    }
                    return result;
                });
    }
}
