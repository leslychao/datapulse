package io.datapulse.sellerops.persistence;

import io.datapulse.sellerops.domain.GridFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class GridPostgresReadRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final Map<String, String> SORT_WHITELIST = Map.ofEntries(
            Map.entry("skuCode", "ss.sku_code"),
            Map.entry("productName", "mo.name"),
            Map.entry("marketplaceType", "mc.marketplace_type"),
            Map.entry("connectionName", "mc.name"),
            Map.entry("status", "mo.status"),
            Map.entry("category", "cat.name"),
            Map.entry("currentPrice", "cpc.price"),
            Map.entry("discountPrice", "cpc.discount_price"),
            Map.entry("costPrice", "cp.cost_price"),
            Map.entry("marginPct", "(cpc.price - cp.cost_price) / NULLIF(cpc.price, 0)"),
            Map.entry("availableStock", "stock_agg.total_available"),
            Map.entry("lastDecision", "latest_pd.decision_type"),
            Map.entry("lastActionStatus", "latest_pa.status"),
            Map.entry("lastSyncAt", "mss.last_success_at")
    );

    private static final String BASE_SELECT = """
            SELECT
                mo.id                       AS offer_id,
                ss.id                       AS seller_sku_id,
                ss.sku_code,
                mo.name                     AS product_name,
                mc.marketplace_type,
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
                pp.name                     AS active_policy,
                latest_pd.decision_type     AS last_decision,
                latest_pa.status            AS last_action_status,
                CASE WHEN latest_pa.status = 'PENDING_APPROVAL' THEN latest_pa.action_id ELSE NULL END AS pending_action_id,
                active_promo.participation_status AS promo_status,
                mpl.id IS NOT NULL          AS manual_lock,
                sos.simulated_price,
                sos.price_delta_pct         AS simulated_delta_pct,
                mss.last_success_at         AS last_sync_at,
                bid_asgn.policy_name        AS bid_policy_name,
                bid_asgn.strategy_type      AS bid_strategy_type,
                latest_bd.current_bid,
                latest_bd.decision_type     AS last_bid_decision_type,
                mbl.id IS NOT NULL          AS manual_bid_lock,
                mbl.id                      AS bid_lock_id
            """;

    private static final String FROM_JOINS = """
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
                SELECT pp2.name
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
                SELECT decision_type
                FROM price_decision
                WHERE marketplace_offer_id = mo.id
                ORDER BY created_at DESC
                LIMIT 1
            ) latest_pd ON true
            LEFT JOIN LATERAL (
                SELECT id AS action_id, status
                FROM price_action
                WHERE marketplace_offer_id = mo.id
                ORDER BY created_at DESC
                LIMIT 1
            ) latest_pa ON true
            LEFT JOIN LATERAL (
                SELECT cpp.participation_status
                FROM canonical_promo_product cpp
                JOIN canonical_promo_campaign cpcam ON cpcam.id = cpp.canonical_promo_campaign_id
                WHERE cpp.marketplace_offer_id = mo.id
                  AND cpp.participation_status = 'PARTICIPATING'
                  AND (cpcam.date_to IS NULL OR cpcam.date_to > NOW())
                LIMIT 1
            ) active_promo ON true
            LEFT JOIN manual_price_lock mpl
                ON mpl.marketplace_offer_id = mo.id AND mpl.unlocked_at IS NULL
            LEFT JOIN simulated_offer_state sos
                ON sos.marketplace_offer_id = mo.id AND sos.workspace_id = mc.workspace_id
            LEFT JOIN LATERAL (
                SELECT MIN(last_success_at) AS last_success_at
                FROM marketplace_sync_state
                WHERE marketplace_connection_id = mc.id
            ) mss ON true
            LEFT JOIN LATERAL (
                SELECT bp.name AS policy_name, bp.strategy_type
                FROM bid_policy_assignment bpa
                JOIN bid_policy bp ON bp.id = bpa.bid_policy_id AND bp.status = 'ACTIVE'
                WHERE bpa.marketplace_offer_id = mo.id
                LIMIT 1
            ) bid_asgn ON true
            LEFT JOIN LATERAL (
                SELECT bd.current_bid, bd.decision_type
                FROM bid_decision bd
                WHERE bd.marketplace_offer_id = mo.id
                ORDER BY bd.created_at DESC
                LIMIT 1
            ) latest_bd ON true
            LEFT JOIN manual_bid_lock mbl
                ON mbl.marketplace_offer_id = mo.id
                AND (mbl.expires_at IS NULL OR mbl.expires_at > now())
            """;

    private static final String BASE_COUNT = "SELECT COUNT(*) " + FROM_JOINS;

    public Page<GridRow> findAll(long workspaceId, GridFilter filter, Pageable pageable) {
        var where = new StringBuilder(" WHERE mc.workspace_id = :workspaceId");
        var params = new MapSqlParameterSource("workspaceId", workspaceId);

        appendFilters(filter, where, params);

        String countSql = BASE_COUNT + where;
        Long total = jdbc.queryForObject(countSql, params, Long.class);
        if (total == null || total == 0) {
            return Page.empty(pageable);
        }

        String orderBy = buildOrderByClause(pageable.getSort());
        String querySql = BASE_SELECT + FROM_JOINS + where + orderBy
                + " LIMIT :limit OFFSET :offset";
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<GridRow> content = jdbc.query(querySql, params, this::mapGridRow);
        return new PageImpl<>(content, pageable, total);
    }

    public List<GridRow> findBatchForExport(long workspaceId, GridFilter filter,
                                            int batchSize, int offset) {
        var where = new StringBuilder(" WHERE mc.workspace_id = :workspaceId");
        var params = new MapSqlParameterSource("workspaceId", workspaceId);

        appendFilters(filter, where, params);

        String orderBy = " ORDER BY ss.sku_code ASC NULLS LAST";
        String sql = BASE_SELECT + FROM_JOINS + where + orderBy
            + " LIMIT :limit OFFSET :offset";
        params.addValue("limit", batchSize);
        params.addValue("offset", offset);

        return jdbc.query(sql, params, this::mapGridRow);
    }

    private void appendFilters(GridFilter filter, StringBuilder where,
                               MapSqlParameterSource params) {
        if (filter == null) {
            return;
        }

        if (filter.marketplaceType() != null && !filter.marketplaceType().isEmpty()) {
            where.append(" AND mc.marketplace_type IN (:marketplaceTypes)");
            params.addValue("marketplaceTypes", filter.marketplaceType());
        }

        if (filter.connectionId() != null && !filter.connectionId().isEmpty()) {
            where.append(" AND mc.id IN (:connectionIds)");
            params.addValue("connectionIds", filter.connectionId());
        }

        if (filter.status() != null && !filter.status().isEmpty()) {
            where.append(" AND mo.status IN (:statuses)");
            params.addValue("statuses", filter.status());
        }

        if (StringUtils.hasText(filter.skuCode())) {
            where.append(" AND ss.sku_code ILIKE :skuCode");
            params.addValue("skuCode", "%%" + filter.skuCode().trim() + "%%");
        }

        if (StringUtils.hasText(filter.productName())) {
            where.append(" AND mo.name ILIKE :productName");
            params.addValue("productName", "%%" + filter.productName().trim() + "%%");
        }

        if (filter.categoryId() != null && !filter.categoryId().isEmpty()) {
            where.append(" AND mo.category_id IN (:categoryIds)");
            params.addValue("categoryIds", filter.categoryId());
        }

        if (filter.marginMin() != null) {
            where.append("""
                     AND CASE WHEN cpc.price > 0 AND cp.cost_price IS NOT NULL
                              THEN (cpc.price - cp.cost_price) / NULLIF(cpc.price, 0) * 100
                              ELSE NULL END >= :marginMin
                    """);
            params.addValue("marginMin", filter.marginMin());
        }

        if (filter.marginMax() != null) {
            where.append("""
                     AND CASE WHEN cpc.price > 0 AND cp.cost_price IS NOT NULL
                              THEN (cpc.price - cp.cost_price) / NULLIF(cpc.price, 0) * 100
                              ELSE NULL END <= :marginMax
                    """);
            params.addValue("marginMax", filter.marginMax());
        }

        if (filter.hasManualLock() != null) {
            if (filter.hasManualLock()) {
                where.append(" AND mpl.id IS NOT NULL");
            } else {
                where.append(" AND mpl.id IS NULL");
            }
        }

        if (filter.hasActivePromo() != null) {
            if (filter.hasActivePromo()) {
                where.append(" AND active_promo.participation_status IS NOT NULL");
            } else {
                where.append(" AND active_promo.participation_status IS NULL");
            }
        }

        if (StringUtils.hasText(filter.lastDecision())) {
            where.append(" AND latest_pd.decision_type = :lastDecision");
            params.addValue("lastDecision", filter.lastDecision().trim());
        }

        if (StringUtils.hasText(filter.lastActionStatus())) {
            where.append(" AND latest_pa.status = :lastActionStatus");
            params.addValue("lastActionStatus", filter.lastActionStatus().trim());
        }

        if (filter.offerIds() != null && !filter.offerIds().isEmpty()) {
            where.append(" AND mo.id IN (:preFilterOfferIds)");
            params.addValue("preFilterOfferIds", filter.offerIds());
        }
    }

    String buildOrderByClause(Sort sort) {
        if (sort.isUnsorted()) {
            return " ORDER BY ss.sku_code ASC NULLS LAST";
        }

        var sb = new StringBuilder(" ORDER BY ");
        var orders = sort.stream().toList();
        for (int i = 0; i < orders.size(); i++) {
            Sort.Order order = orders.get(i);
            String column = SORT_WHITELIST.getOrDefault(order.getProperty(), "ss.sku_code");
            sb.append(column).append(" ").append(order.getDirection().name()).append(" NULLS LAST");
            if (i < orders.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    public boolean isSortableColumn(String column) {
        return SORT_WHITELIST.containsKey(column);
    }

    private GridRow mapGridRow(ResultSet rs, int rowNum) throws SQLException {
        return GridRow.builder()
                .offerId(rs.getLong("offer_id"))
                .sellerSkuId(rs.getLong("seller_sku_id"))
                .skuCode(rs.getString("sku_code"))
                .productName(rs.getString("product_name"))
                .marketplaceType(rs.getString("marketplace_type"))
                .connectionName(rs.getString("connection_name"))
                .status(rs.getString("status"))
                .category(rs.getString("category"))
                .currentPrice(rs.getBigDecimal("current_price"))
                .discountPrice(rs.getBigDecimal("discount_price"))
                .costPrice(rs.getBigDecimal("cost_price"))
                .marginPct(rs.getBigDecimal("margin_pct"))
                .availableStock(getBoxedInt(rs, "available_stock"))
                .activePolicy(rs.getString("active_policy"))
                .lastDecision(rs.getString("last_decision"))
                .lastActionStatus(rs.getString("last_action_status"))
                .pendingActionId(getBoxedLong(rs, "pending_action_id"))
                .promoStatus(rs.getString("promo_status"))
                .manualLock(rs.getBoolean("manual_lock"))
                .simulatedPrice(rs.getBigDecimal("simulated_price"))
                .simulatedDeltaPct(rs.getBigDecimal("simulated_delta_pct"))
                .lastSyncAt(rs.getObject("last_sync_at", OffsetDateTime.class))
                .bidPolicyName(rs.getString("bid_policy_name"))
                .bidStrategyType(rs.getString("bid_strategy_type"))
                .currentBid(getBoxedInt(rs, "current_bid"))
                .lastBidDecisionType(rs.getString("last_bid_decision_type"))
                .manualBidLock(rs.getBoolean("manual_bid_lock"))
                .bidLockId(getBoxedLong(rs, "bid_lock_id"))
                .build();
    }

    public List<Long> findMatchingOfferIds(long workspaceId, GridFilter filter, int maxIds) {
        var where = new StringBuilder(" WHERE mc.workspace_id = :workspaceId");
        var params = new MapSqlParameterSource("workspaceId", workspaceId);
        appendFilters(filter, where, params);

        String sql = "SELECT mo.id " + FROM_JOINS + where + " LIMIT :limit";
        params.addValue("limit", maxIds);
        return jdbc.queryForList(sql, params, Long.class);
    }

    public List<GridRow> findByOrderedIds(long workspaceId, List<Long> orderedOfferIds) {
        if (orderedOfferIds == null || orderedOfferIds.isEmpty()) {
            return List.of();
        }
        String sql = BASE_SELECT + FROM_JOINS
                + " WHERE mc.workspace_id = :workspaceId AND mo.id IN (:offerIds)"
                + " ORDER BY array_position(ARRAY[" + joinIds(orderedOfferIds) + "], mo.id)";
        var params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("offerIds", orderedOfferIds);
        return jdbc.query(sql, params, this::mapGridRow);
    }

    private String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    public GridKpiRow findKpi(long workspaceId) {
        var params = new MapSqlParameterSource("workspaceId", workspaceId);
        return jdbc.queryForObject(KPI_SQL, params, this::mapKpiRow);
    }

    public List<Long> findConnectionIds(long workspaceId) {
        return jdbc.queryForList(
                "SELECT id FROM marketplace_connection WHERE workspace_id = :workspaceId",
                new MapSqlParameterSource("workspaceId", workspaceId),
                Long.class);
    }

    private static final String KPI_SQL = """
            SELECT
                COUNT(*)                    AS total_offers,
                AVG(
                    CASE WHEN cpc.price > 0 AND cp.cost_price IS NOT NULL
                         THEN ROUND((cpc.price - cp.cost_price) / NULLIF(cpc.price, 0) * 100, 2)
                         ELSE NULL END
                )                           AS avg_margin_pct,
                (
                    SELECT COUNT(*)
                    FROM price_action pa
                    WHERE pa.workspace_id = :workspaceId
                      AND pa.status IN ('PENDING_APPROVAL', 'APPROVED', 'ON_HOLD', 'SCHEDULED')
                )                           AS pending_actions_count
            FROM marketplace_offer mo
            JOIN marketplace_connection mc ON mc.id = mo.marketplace_connection_id
            LEFT JOIN canonical_price_current cpc ON cpc.marketplace_offer_id = mo.id
            LEFT JOIN LATERAL (
                SELECT cost_price
                FROM cost_profile
                WHERE seller_sku_id = mo.seller_sku_id
                  AND valid_from <= CURRENT_DATE
                  AND (valid_to IS NULL OR valid_to > CURRENT_DATE)
                ORDER BY valid_from DESC
                LIMIT 1
            ) cp ON true
            WHERE mc.workspace_id = :workspaceId
            """;

    private GridKpiRow mapKpiRow(ResultSet rs, int rowNum) throws SQLException {
        return new GridKpiRow(
                rs.getLong("total_offers"),
                rs.getBigDecimal("avg_margin_pct"),
                rs.getLong("pending_actions_count"));
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
