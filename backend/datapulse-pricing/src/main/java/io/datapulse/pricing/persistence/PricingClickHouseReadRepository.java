package io.datapulse.pricing.persistence;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PricingClickHouseReadRepository {

    private final NamedParameterJdbcTemplate ch;

    public PricingClickHouseReadRepository(
            @Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate) {
        this.ch = new NamedParameterJdbcTemplate(clickhouseJdbcTemplate);
    }

    // ── ad cost ──────────────────────────────────────────────────────────────

    private static final String AD_COST_RATIOS = """
            SELECT
                fa.marketplace_sku,
                sum(fa.spend) / nullIf(sum(ff.revenue_amount), 0) AS ad_cost_ratio
            FROM fact_advertising AS fa
            INNER JOIN dim_product AS dp
                ON fa.connection_id = dp.connection_id
                AND fa.marketplace_sku = dp.marketplace_sku
            LEFT JOIN fact_finance AS ff
                ON dp.connection_id = ff.connection_id
                AND dp.seller_sku_id = ff.seller_sku_id
                AND toYYYYMM(fa.ad_date) = toYYYYMM(ff.finance_date)
            WHERE fa.marketplace_sku IN (:skus)
                AND fa.ad_date >= today() - :lookbackDays
                AND ff.finance_date >= today() - :lookbackDays
                AND ff.attribution_level IN ('POSTING', 'PRODUCT')
            GROUP BY fa.marketplace_sku
            SETTINGS final = 1
            """;

    public Map<String, BigDecimal> findAdCostRatios(List<String> marketplaceSkus,
                                                    int lookbackDays) {
        if (marketplaceSkus.isEmpty()) {
            return Collections.emptyMap();
        }
        var params = new MapSqlParameterSource()
                .addValue("skus", marketplaceSkus)
                .addValue("lookbackDays", lookbackDays);
        return ch.query(AD_COST_RATIOS, params, rs -> {
            Map<String, BigDecimal> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getString("marketplace_sku"),
                        rs.getBigDecimal("ad_cost_ratio"));
            }
            return result;
        });
    }

    // ── avg commission pct (per-SKU) ─────────────────────────────────────────

    private static final String AVG_COMMISSION_PCT = """
            SELECT
                coalesce(mp.seller_sku_id, 0) AS seller_sku_id,
                (abs(sum(mp.marketplace_commission_amount))
                    + abs(sum(mp.acquiring_commission_amount)))
                    / nullIf(sum(mp.revenue_amount), 0) AS avg_commission_pct,
                count(*) AS transaction_count
            FROM mart_posting_pnl AS mp
            WHERE mp.connection_id = :connectionId
              AND mp.seller_sku_id IN (:sellerSkuIds)
              AND mp.finance_date >= today() - :lookbackDays
            GROUP BY mp.seller_sku_id
            HAVING transaction_count >= :minTransactions
            SETTINGS final = 1
            """;

    public Map<Long, CommissionResult> findAvgCommissionPct(
            long connectionId, List<Long> sellerSkuIds,
            int lookbackDays, int minTransactions) {
        if (sellerSkuIds.isEmpty()) {
            return Collections.emptyMap();
        }
        var params = new MapSqlParameterSource()
                .addValue("connectionId", connectionId)
                .addValue("sellerSkuIds", sellerSkuIds)
                .addValue("lookbackDays", lookbackDays)
                .addValue("minTransactions", minTransactions);
        return ch.query(AVG_COMMISSION_PCT, params, rs -> {
            Map<Long, CommissionResult> result = new HashMap<>();
            while (rs.next()) {
                long skuId = rs.getLong("seller_sku_id");
                if (skuId == 0) {
                    continue;
                }
                result.put(skuId, new CommissionResult(
                        rs.getBigDecimal("avg_commission_pct"),
                        rs.getInt("transaction_count")));
            }
            return result;
        });
    }

    public record CommissionResult(BigDecimal commissionPct, int transactionCount) {}

    // ── avg commission pct (per-category fallback) ───────────────────────────

    private static final String CATEGORY_AVG_COMMISSION_PCT = """
            SELECT
                dp.category AS category_name,
                (abs(sum(mp.marketplace_commission_amount))
                    + abs(sum(mp.acquiring_commission_amount)))
                    / nullIf(sum(mp.revenue_amount), 0) AS avg_commission_pct
            FROM mart_posting_pnl AS mp
            INNER JOIN dim_product AS dp
                ON mp.product_id = dp.product_id AND mp.connection_id = dp.connection_id
            WHERE mp.connection_id = :connectionId
              AND dp.category IN (:categories)
              AND mp.finance_date >= today() - :lookbackDays
            GROUP BY dp.category
            SETTINGS final = 1
            """;

    public Map<String, BigDecimal> findCategoryAvgCommissionPct(
            long connectionId, List<String> categories, int lookbackDays) {
        if (categories.isEmpty()) {
            return Collections.emptyMap();
        }
        var params = new MapSqlParameterSource()
                .addValue("connectionId", connectionId)
                .addValue("categories", categories)
                .addValue("lookbackDays", lookbackDays);
        return ch.query(CATEGORY_AVG_COMMISSION_PCT, params, rs -> {
            Map<String, BigDecimal> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getString("category_name"),
                        rs.getBigDecimal("avg_commission_pct"));
            }
            return result;
        });
    }

    // ── avg logistics per unit ───────────────────────────────────────────────

    private static final String AVG_LOGISTICS_PER_UNIT = """
            SELECT
                coalesce(mp.seller_sku_id, 0) AS seller_sku_id,
                abs(sum(mp.logistics_cost_amount))
                    / nullIf(sum(mp.quantity), 0) AS avg_logistics_per_unit
            FROM mart_posting_pnl AS mp
            WHERE mp.connection_id = :connectionId
              AND mp.seller_sku_id IN (:sellerSkuIds)
              AND mp.finance_date >= today() - :lookbackDays
              AND mp.quantity IS NOT NULL
              AND mp.quantity > 0
            GROUP BY mp.seller_sku_id
            SETTINGS final = 1
            """;

    public Map<Long, BigDecimal> findAvgLogisticsPerUnit(
            long connectionId, List<Long> sellerSkuIds, int lookbackDays) {
        if (sellerSkuIds.isEmpty()) {
            return Collections.emptyMap();
        }
        var params = new MapSqlParameterSource()
                .addValue("connectionId", connectionId)
                .addValue("sellerSkuIds", sellerSkuIds)
                .addValue("lookbackDays", lookbackDays);
        return ch.query(AVG_LOGISTICS_PER_UNIT, params, rs -> {
            Map<Long, BigDecimal> result = new HashMap<>();
            while (rs.next()) {
                long skuId = rs.getLong("seller_sku_id");
                if (skuId == 0) {
                    continue;
                }
                result.put(skuId, rs.getBigDecimal("avg_logistics_per_unit"));
            }
            return result;
        });
    }

    // ── return rate pct ──────────────────────────────────────────────────────

    private static final String RETURN_RATE_PCT = """
            SELECT
                fs.seller_sku_id,
                coalesce(
                    toDecimal64(fr.return_qty, 4) / nullIf(fs.sale_qty, 0),
                    0
                ) AS return_rate_pct
            FROM (
                SELECT seller_sku_id, sum(quantity) AS sale_qty
                FROM fact_sales
                WHERE connection_id = :connectionId
                  AND seller_sku_id IN (:sellerSkuIds)
                  AND sale_date >= today() - :lookbackDays
                GROUP BY seller_sku_id
            ) AS fs
            LEFT JOIN (
                SELECT seller_sku_id, sum(quantity) AS return_qty
                FROM fact_returns
                WHERE connection_id = :connectionId
                  AND seller_sku_id IN (:sellerSkuIds)
                  AND return_date >= today() - :lookbackDays
                GROUP BY seller_sku_id
            ) AS fr ON fs.seller_sku_id = fr.seller_sku_id
            SETTINGS final = 1
            """;

    public Map<Long, BigDecimal> findReturnRatePct(
            long connectionId, List<Long> sellerSkuIds, int lookbackDays) {
        if (sellerSkuIds.isEmpty()) {
            return Collections.emptyMap();
        }
        var params = new MapSqlParameterSource()
                .addValue("connectionId", connectionId)
                .addValue("sellerSkuIds", sellerSkuIds)
                .addValue("lookbackDays", lookbackDays);
        return ch.query(RETURN_RATE_PCT, params, rs -> {
            Map<Long, BigDecimal> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getLong("seller_sku_id"),
                        rs.getBigDecimal("return_rate_pct"));
            }
            return result;
        });
    }

    // ── seller_sku_id → category (from dim_product) ─────────────────────────

    private static final String SKU_CATEGORIES = """
            SELECT seller_sku_id, category
            FROM dim_product
            WHERE connection_id = :connectionId
              AND seller_sku_id IN (:sellerSkuIds)
              AND category IS NOT NULL
              AND category != ''
            SETTINGS final = 1
            """;

    public Map<Long, String> findCategoriesBySellerSkuIds(
            long connectionId, List<Long> sellerSkuIds) {
        if (sellerSkuIds.isEmpty()) {
            return Collections.emptyMap();
        }
        var params = new MapSqlParameterSource()
                .addValue("connectionId", connectionId)
                .addValue("sellerSkuIds", sellerSkuIds);
        return ch.query(SKU_CATEGORIES, params, rs -> {
            Map<Long, String> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getLong("seller_sku_id"),
                        rs.getString("category"));
            }
            return result;
        });
    }
}
